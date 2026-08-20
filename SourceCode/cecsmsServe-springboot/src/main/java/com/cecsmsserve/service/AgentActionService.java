package com.cecsmsserve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cecsmsserve.entity.AgentAction;
import com.cecsmsserve.mapper.AgentActionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class AgentActionService {

    private static final Logger log = LoggerFactory.getLogger(AgentActionService.class);

    public static final String PENDING = "PENDING";
    public static final String EXECUTING = "EXECUTING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    private static final TypeReference<Map<String, Object>> ARGUMENT_TYPE = new TypeReference<>() { };

    private final AgentActionMapper mapper;
    private final ToolExecutor toolExecutor;
    private final JsonMapper jsonMapper;
    private final Duration confirmationTtl;
    private final int historyLimit;

    public AgentActionService(
            AgentActionMapper mapper,
            ToolExecutor toolExecutor,
            JsonMapper jsonMapper,
            @Value("${agent.confirmation-ttl:10m}") Duration confirmationTtl,
            @Value("${agent.history-limit:20}") int historyLimit) {
        this.mapper = mapper;
        this.toolExecutor = toolExecutor;
        this.jsonMapper = jsonMapper;
        this.confirmationTtl = confirmationTtl.isNegative() || confirmationTtl.isZero()
                ? Duration.ofMinutes(10) : confirmationTtl;
        this.historyLimit = Math.max(1, Math.min(historyLimit, 100));
    }

    @Transactional
    public ActionView createPending(Integer userId, ToolExecutor.ToolPlan plan) {
        if (userId == null || plan == null || !plan.requiresConfirmation()) {
            throw new IllegalArgumentException("待确认操作参数无效");
        }

        String argumentsJson = writeArguments(plan.arguments());
        String requestKey = sha256(userId + "\n" + plan.name() + "\n" + argumentsJson);
        LocalDateTime now = LocalDateTime.now();
        AgentAction existing = mapper.selectOne(new LambdaQueryWrapper<AgentAction>()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getRequestKey, requestKey)
                .eq(AgentAction::getStatus, PENDING)
                .gt(AgentAction::getExpiresAt, now)
                .orderByDesc(AgentAction::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return toView(existing);
        }

        AgentAction action = new AgentAction();
        action.setConfirmationToken(UUID.randomUUID().toString().replace("-", ""));
        action.setRequestKey(requestKey);
        action.setUserId(userId);
        action.setToolName(plan.name());
        action.setArgumentsJson(argumentsJson);
        action.setSummary(truncate(plan.summary(), 500));
        action.setRequiresConfirmation(true);
        action.setStatus(PENDING);
        action.setExpiresAt(now.plus(confirmationTtl));
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        if (mapper.insert(action) != 1) {
            throw new IllegalStateException("无法创建待确认操作");
        }
        return toView(action);
    }

    @Transactional
    public void recordImmediate(Integer userId, ToolExecutor.ToolPlan plan, ToolExecutor.ToolResult result) {
        LocalDateTime now = LocalDateTime.now();
        AgentAction action = new AgentAction();
        action.setRequestKey(sha256(userId + "\n" + plan.name() + "\n" + now + "\n" + UUID.randomUUID()));
        action.setUserId(userId);
        action.setToolName(plan.name());
        action.setSummary(truncate(plan.summary(), 500));
        action.setRequiresConfirmation(false);
        action.setStatus(result.success() ? SUCCEEDED : FAILED);
        action.setResultMessage(truncate(result.content(), 1000));
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        action.setExecutedAt(now);
        mapper.insert(action);
    }

    /** WorkBuddy observability is best effort and must never break an MCP response. */
    public void recordIntegration(String toolName, boolean success, String resultMessage) {
        try {
            LocalDateTime now = LocalDateTime.now();
            AgentAction action = new AgentAction();
            action.setRequestKey(sha256("workbuddy\n" + toolName + "\n" + now + "\n" + UUID.randomUUID()));
            action.setUserId(-1);
            action.setToolName(truncate("mcp:" + valueOrDefault(toolName, "unknown"), 100));
            action.setSummary(truncate("WorkBuddy 调用 " + valueOrDefault(toolName, "未知工具"), 500));
            action.setRequiresConfirmation(false);
            action.setStatus(success ? SUCCEEDED : FAILED);
            action.setResultMessage(truncate(resultMessage, 1000));
            action.setCreatedAt(now);
            action.setUpdatedAt(now);
            action.setExecutedAt(now);
            mapper.insert(action);
        } catch (Exception ex) {
            String safeToolName = toolName == null ? "unknown" : toolName.replace('\r', '_').replace('\n', '_');
            log.warn("Unable to persist WorkBuddy interaction for {} ({})",
                    safeToolName, ex.getClass().getSimpleName());
        }
    }

    @Transactional
    public ActionExecution confirm(Integer userId, String token) {
        AgentAction action = requireOwnedAction(userId, token);
        if (SUCCEEDED.equals(action.getStatus())) {
            return new ActionExecution(true, valueOrDefault(action.getResultMessage(), "操作已完成"), toView(action));
        }
        if (FAILED.equals(action.getStatus()) || CANCELLED.equals(action.getStatus()) || EXPIRED.equals(action.getStatus())) {
            return new ActionExecution(false, terminalMessage(action), toView(action));
        }
        if (EXECUTING.equals(action.getStatus())) {
            return new ActionExecution(false, "操作正在执行，请稍后刷新记录", toView(action));
        }

        LocalDateTime now = LocalDateTime.now();
        if (action.getExpiresAt() == null || !action.getExpiresAt().isAfter(now)) {
            expire(action.getId(), now);
            action.setStatus(EXPIRED);
            action.setArgumentsJson(null);
            return new ActionExecution(false, "确认已过期，请重新发起操作", toView(action));
        }

        int claimed = mapper.update(null, new LambdaUpdateWrapper<AgentAction>()
                .eq(AgentAction::getId, action.getId())
                .eq(AgentAction::getStatus, PENDING)
                .gt(AgentAction::getExpiresAt, now)
                .set(AgentAction::getStatus, EXECUTING)
                .set(AgentAction::getUpdatedAt, now));
        if (claimed != 1) {
            action.setStatus(EXECUTING);
            return new ActionExecution(false, "操作正在执行或已经处理，请刷新记录", toView(action));
        }

        ToolExecutor.ToolResult result;
        try {
            Map<String, Object> arguments = readArguments(action.getArgumentsJson());
            ToolExecutor.ToolPlan plan = toolExecutor.prepare(action.getToolName(), arguments, userId);
            result = toolExecutor.execute(plan, userId);
        } catch (IllegalArgumentException ex) {
            result = new ToolExecutor.ToolResult(false, "执行失败：" + ex.getMessage());
        } catch (Exception ex) {
            log.error("Confirmed Agent action failed before tool execution: actionId={}, userId={}",
                    action.getId(), userId, ex);
            result = new ToolExecutor.ToolResult(false, "执行失败：操作数据异常，请重新发起");
        }
        String finalStatus = result.success() ? SUCCEEDED : FAILED;
        LocalDateTime finishedAt = LocalDateTime.now();
        int finalized = mapper.update(null, new LambdaUpdateWrapper<AgentAction>()
                .eq(AgentAction::getId, action.getId())
                .eq(AgentAction::getStatus, EXECUTING)
                .set(AgentAction::getStatus, finalStatus)
                .set(AgentAction::getResultMessage, truncate(result.content(), 1000))
                .set(AgentAction::getArgumentsJson, null)
                .set(AgentAction::getExecutedAt, finishedAt)
                .set(AgentAction::getUpdatedAt, finishedAt));
        if (finalized != 1) {
            throw new IllegalStateException("操作结果无法安全落库，本次事务已回滚");
        }

        action.setStatus(finalStatus);
        action.setResultMessage(truncate(result.content(), 1000));
        action.setArgumentsJson(null);
        action.setExecutedAt(finishedAt);
        action.setUpdatedAt(finishedAt);
        return new ActionExecution(result.success(), result.content(), toView(action));
    }

    @Transactional
    public ActionExecution cancel(Integer userId, String token) {
        AgentAction action = requireOwnedAction(userId, token);
        if (CANCELLED.equals(action.getStatus())) {
            return new ActionExecution(true, "操作已取消", toView(action));
        }
        if (!PENDING.equals(action.getStatus())) {
            return new ActionExecution(false, terminalMessage(action), toView(action));
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.update(null, new LambdaUpdateWrapper<AgentAction>()
                .eq(AgentAction::getId, action.getId())
                .eq(AgentAction::getStatus, PENDING)
                .set(AgentAction::getStatus, CANCELLED)
                .set(AgentAction::getResultMessage, "用户取消了操作")
                .set(AgentAction::getArgumentsJson, null)
                .set(AgentAction::getUpdatedAt, now));
        if (updated != 1) {
            return new ActionExecution(false, "操作正在执行或已经处理，请刷新记录", toView(action));
        }
        action.setStatus(CANCELLED);
        action.setResultMessage("用户取消了操作");
        action.setArgumentsJson(null);
        action.setUpdatedAt(now);
        return new ActionExecution(true, "操作已取消", toView(action));
    }

    @Transactional
    public List<ActionView> history(Integer userId, int requestedLimit) {
        LocalDateTime now = LocalDateTime.now();
        mapper.update(null, new LambdaUpdateWrapper<AgentAction>()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getStatus, PENDING)
                .le(AgentAction::getExpiresAt, now)
                .set(AgentAction::getStatus, EXPIRED)
                .set(AgentAction::getResultMessage, "确认已过期")
                .set(AgentAction::getArgumentsJson, null)
                .set(AgentAction::getUpdatedAt, now));
        int limit = Math.max(1, Math.min(requestedLimit, historyLimit));
        return mapper.selectList(new LambdaQueryWrapper<AgentAction>()
                        .eq(AgentAction::getUserId, userId)
                        .orderByDesc(AgentAction::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(this::toView)
                .toList();
    }

    public List<AdminActionView> adminHistory(String toolName, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        LambdaQueryWrapper<AgentAction> query = new LambdaQueryWrapper<AgentAction>()
                .orderByDesc(AgentAction::getId)
                .last("LIMIT " + limit);
        if (toolName != null && !toolName.isBlank()) {
            query.eq(AgentAction::getToolName, toolName.trim());
        }
        return mapper.selectList(query).stream().map(this::toAdminView).toList();
    }

    public List<AdminActionView> integrationHistory(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return mapper.selectList(new LambdaQueryWrapper<AgentAction>()
                        .eq(AgentAction::getUserId, -1)
                        .likeRight(AgentAction::getToolName, "mcp:")
                        .orderByDesc(AgentAction::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(this::toAdminView)
                .toList();
    }

    private AgentAction requireOwnedAction(Integer userId, String token) {
        if (userId == null || token == null || !token.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("确认令牌无效");
        }
        AgentAction action = mapper.selectOne(new LambdaQueryWrapper<AgentAction>()
                .eq(AgentAction::getUserId, userId)
                .eq(AgentAction::getConfirmationToken, token)
                .last("LIMIT 1"));
        if (action == null) {
            throw new IllegalArgumentException("待确认操作不存在或不属于当前用户");
        }
        return action;
    }

    private void expire(Long id, LocalDateTime now) {
        mapper.update(null, new LambdaUpdateWrapper<AgentAction>()
                .eq(AgentAction::getId, id)
                .eq(AgentAction::getStatus, PENDING)
                .set(AgentAction::getStatus, EXPIRED)
                .set(AgentAction::getResultMessage, "确认已过期")
                .set(AgentAction::getArgumentsJson, null)
                .set(AgentAction::getUpdatedAt, now));
    }

    private String writeArguments(Map<String, Object> arguments) {
        try {
            return jsonMapper.writeValueAsString(new TreeMap<>(arguments));
        } catch (Exception ex) {
            throw new IllegalArgumentException("操作参数无法保存");
        }
    }

    private Map<String, Object> readArguments(String argumentsJson) {
        try {
            return jsonMapper.readValue(argumentsJson, ARGUMENT_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("待确认操作参数已损坏");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成操作摘要", ex);
        }
    }

    private ActionView toView(AgentAction action) {
        String token = PENDING.equals(action.getStatus()) ? action.getConfirmationToken() : null;
        return new ActionView(
                action.getId(), token, action.getToolName(), action.getSummary(), action.getStatus(),
                action.getResultMessage(), action.getExpiresAt(), action.getCreatedAt(), action.getExecutedAt());
    }

    private AdminActionView toAdminView(AgentAction action) {
        return new AdminActionView(
                action.getId(), action.getUserId(), action.getToolName(), action.getStatus(),
                action.getCreatedAt(), action.getExecutedAt());
    }

    private String terminalMessage(AgentAction action) {
        return switch (action.getStatus()) {
            case FAILED -> valueOrDefault(action.getResultMessage(), "操作执行失败");
            case CANCELLED -> "操作已取消";
            case EXPIRED -> "确认已过期，请重新发起操作";
            case SUCCEEDED -> valueOrDefault(action.getResultMessage(), "操作已完成");
            default -> "操作当前不可处理，请刷新记录";
        };
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ActionExecution(boolean success, String message, ActionView action) { }

    public record ActionView(
            Long id,
            String confirmationToken,
            String toolName,
            String summary,
            String status,
            String resultMessage,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            LocalDateTime executedAt) { }

    public record AdminActionView(
            Long id,
            Integer userId,
            String toolName,
            String status,
            LocalDateTime createdAt,
            LocalDateTime executedAt) { }
}
