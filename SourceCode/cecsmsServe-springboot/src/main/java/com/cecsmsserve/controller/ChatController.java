package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentActionService;
import com.cecsmsserve.service.AgentActionService.ActionExecution;
import com.cecsmsserve.service.AgentActionService.ActionView;
import com.cecsmsserve.service.AgentRateLimiter;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.service.AiProviderException;
import com.cecsmsserve.service.AiProviderService;
import com.cecsmsserve.service.AiProviderService.ProviderSelection;
import com.cecsmsserve.service.AiProviderService.TokenUsage;
import com.cecsmsserve.service.AiSafetyService;
import com.cecsmsserve.service.CareRecommendationService;
import com.cecsmsserve.service.KnowledgeBaseService;
import com.cecsmsserve.service.PromptTemplateService;
import com.cecsmsserve.service.ToolExecutor;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.Tools;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_TOTAL_MESSAGE_CHARS = 20_000;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOTAL_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final TypeReference<Map<String, Object>> ARGUMENT_TYPE = new TypeReference<>() { };
    private final ToolExecutor toolExecutor;
    private final AgentActionService actionService;
    private final AgentRateLimiter rateLimiter;
    private final AgentRunService runService;
    private final AiProviderService providerService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;
    private final AiSafetyService safetyService;
    private final CareRecommendationService careRecommendationService;
    private final JsonMapper jsonMapper;
    private final boolean mcpConfigured;
    private final int maxToolIterations;

    public ChatController(
            ToolExecutor toolExecutor,
            AgentActionService actionService,
            AgentRateLimiter rateLimiter,
            AgentRunService runService,
            AiProviderService providerService,
            KnowledgeBaseService knowledgeBaseService,
            PromptTemplateService promptTemplateService,
            AiSafetyService safetyService,
            CareRecommendationService careRecommendationService,
            JsonMapper jsonMapper,
            @Value("${agent.mcp.api-key:}") String mcpApiKey,
            @Value("${agent.ai.max-tool-iterations:8}") int maxToolIterations) {
        this.toolExecutor = toolExecutor;
        this.actionService = actionService;
        this.rateLimiter = rateLimiter;
        this.runService = runService;
        this.providerService = providerService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.promptTemplateService = promptTemplateService;
        this.safetyService = safetyService;
        this.careRecommendationService = careRecommendationService;
        this.jsonMapper = jsonMapper;
        this.mcpConfigured = mcpApiKey != null && !mcpApiKey.isBlank();
        this.maxToolIterations = Math.max(1, Math.min(maxToolIterations, 12));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<AiProviderService.ProviderView> providers = providerService.statuses();
        boolean configured = providers.stream().anyMatch(AiProviderService.ProviderView::configured);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", configured);
        status.put("defaultProvider", "auto");
        status.put("providers", providers);
        status.put("capabilityCount", Tools.getTools().size());
        status.put("confirmationEnabled", true);
        status.put("browserVoiceEnabled", true);
        status.put("mcpConfigured", mcpConfigured);
        status.put("knowledge", knowledgeBaseService.status());
        status.put("prompts", promptTemplateService.metadata());
        status.put("rateLimit", rateLimiter.status());
        return Map.copyOf(status);
    }

    @PostMapping("/care-plan")
    public ResponseEntity<?> carePlan(
            @Valid @RequestBody CarePlanRequest requestBody,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        AgentRateLimiter.Decision decision = rateLimiter.acquire(userId);
        if (!decision.allowed()) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                    .body(Map.of("error", "请求较频繁，请稍后重试"));
        }
        try {
            return ResponseEntity.ok(careRecommendationService.create(
                    userId, requestBody.needs(), requestBody.provider()));
        } catch (AiProviderException ex) {
            return ResponseEntity.status(ex.getHttpStatus()).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/providers")
    public List<AiProviderService.ProviderView> providers() {
        return providerService.statuses();
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> analytics(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(runService.analytics(userId, days));
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest requestBody,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(ChatResponse.error("请先登录", null, List.of()));
        }

        AgentRateLimiter.Decision decision = rateLimiter.acquire(userId);
        if (!decision.allowed()) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                    .body(ChatResponse.error(
                            "请求较频繁，请在 " + decision.retryAfterSeconds() + " 秒后重试", null, List.of()));
        }

        String runId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        List<TraceStep> trace = new ArrayList<>();
        ProviderSelection provider = null;
        int llmCalls = 0;
        int toolCalls = 0;
        int successfulTools = 0;
        int failedTools = 0;
        TokenUsage tokenUsage = TokenUsage.ZERO;
        String promptVersion = promptTemplateService.metadata(PromptTemplateService.AGENT).version();
        String modality = requestBody.attachments() == null || requestBody.attachments().isEmpty()
                ? "TEXT" : "IMAGE_TEXT";

        try {
            safetyService.assertNoSecrets(requestBody.messages().stream()
                    .map(ChatMessage::content).toList());
            List<ValidatedAttachment> attachments = validateAttachments(requestBody.attachments());
            trace.add(step("INTAKE", "需求已接收", "completed",
                    attachments.isEmpty() ? "文本任务" : "文本 + " + attachments.size() + " 张图片"));

            provider = providerService.resolve(requestBody.provider(), !attachments.isEmpty());
            trace.add(step("ROUTING", "模型路由", "completed",
                    provider.displayName() + " · " + provider.model()));

            List<Map<String, Object>> messages = buildMessages(requestBody.messages(), attachments);
            boolean toolExecuted = false;
            boolean correctionIssued = false;

            for (int iteration = 0; iteration < maxToolIterations; iteration++) {
                Map<String, Object> body = providerService.decorateBody(
                        provider, messages, Tools.getTools(), userId);
                llmCalls++;
                Map<String, Object> apiResponse = providerService.chatCompletion(provider, body);
                tokenUsage = tokenUsage.plus(providerService.usage(apiResponse));
                if (tokenUsage.totalTokens() > providerService.maxTotalTokensPerRun()) {
                    throw new AiProviderException("本次任务已达到 Token 成本上限，请缩短问题或新开会话", 429);
                }
                Map<String, Object> assistantMessage = new LinkedHashMap<>(
                        providerService.firstMessage(apiResponse));
                messages.add(assistantMessage);

                List<?> modelToolCalls = asList(assistantMessage.get("tool_calls"));
                if (!modelToolCalls.isEmpty()) {
                    toolExecuted = true;
                    ToolDispatch dispatch = executeToolCalls(modelToolCalls, messages, userId, trace);
                    toolCalls += dispatch.toolCalls();
                    successfulTools += dispatch.successfulTools();
                    failedTools += dispatch.failedTools();
                    if (dispatch.pendingAction() != null) {
                        RunSummary summary = recordRun(runId, userId, provider, modality,
                                AgentRunService.WAITING_CONFIRMATION, startedNanos, llmCalls, toolCalls,
                                successfulTools, failedTools, true, null, promptVersion, tokenUsage, startedAt);
                        trace.add(step("GUARDRAIL", "等待人工确认", "waiting",
                                "写操作尚未执行，可安全取消"));
                        return ResponseEntity.ok(ChatResponse.pending(
                                "操作已准备好，请核对下方信息并点击“确认执行”。",
                                dispatch.pendingAction(), summary, trace));
                    }
                    continue;
                }

                String reply = assistantMessage.get("content") instanceof String text ? cleanReply(text) : "";
                if (reply.isBlank()) {
                    throw new AiProviderException("AI 没有生成有效回复，请重试", 502);
                }
                if (!toolExecuted && !correctionIssued && isFakeSuccessReply(reply)) {
                    messages.add(Map.of(
                            "role", "user",
                            "content", "你没有调用业务工具，不能宣称操作成功。请调用相应工具并依据真实结果回答。"));
                    correctionIssued = true;
                    trace.add(step("GUARDRAIL", "真实性校验", "corrected",
                            "已阻止无工具依据的成功声明"));
                    continue;
                }
                trace.add(step("RESPONSE", "结果已生成", "completed",
                        toolExecuted ? "已基于真实工具结果回答" : "无需业务工具"));
                RunSummary summary = recordRun(runId, userId, provider, modality,
                        AgentRunService.COMPLETED, startedNanos, llmCalls, toolCalls,
                        successfulTools, failedTools, false, null, promptVersion, tokenUsage, startedAt);
                return ResponseEntity.ok(ChatResponse.success(reply, summary, trace));
            }
            throw new AiProviderException("对话处理超过安全迭代次数，请稍后重试", 502);
        } catch (AiProviderException ex) {
            String fallback = localKnowledgeFallback(requestBody, ex);
            if (fallback != null) {
                trace.add(step("FALLBACK", "模型降级", "corrected",
                        "外部模型不可用，已切换为只读本地知识检索"));
                RunSummary summary = recordRun(runId, userId, provider, modality,
                        AgentRunService.DEGRADED, startedNanos, llmCalls, toolCalls,
                        successfulTools, failedTools, false, "PROVIDER_FALLBACK",
                        promptVersion, tokenUsage, startedAt);
                return ResponseEntity.ok(ChatResponse.success(fallback, summary, trace));
            }
            trace.add(step("FAILED", "模型服务未完成", "failed", ex.getMessage()));
            RunSummary summary = recordRun(runId, userId, provider, modality,
                    AgentRunService.FAILED, startedNanos, llmCalls, toolCalls,
                    successfulTools, failedTools, false, "PROVIDER", promptVersion, tokenUsage, startedAt);
            return ResponseEntity.status(ex.getHttpStatus())
                    .body(ChatResponse.error(ex.getMessage(), summary, trace));
        } catch (IllegalArgumentException ex) {
            trace.add(step("FAILED", "输入或工具校验未通过", "failed", ex.getMessage()));
            RunSummary summary = recordRun(runId, userId, provider, modality,
                    AgentRunService.FAILED, startedNanos, llmCalls, toolCalls,
                    successfulTools, failedTools, false, "VALIDATION", promptVersion, tokenUsage, startedAt);
            return ResponseEntity.badRequest().body(ChatResponse.error(ex.getMessage(), summary, trace));
        } catch (Exception ex) {
            log.error("Agent run failed: runId={}, userId={}", runId, userId, ex);
            trace.add(step("FAILED", "系统暂时无法完成任务", "failed", "请稍后重试"));
            RunSummary summary = recordRun(runId, userId, provider, modality,
                    AgentRunService.FAILED, startedNanos, llmCalls, toolCalls,
                    successfulTools, failedTools, false, "INTERNAL", promptVersion, tokenUsage, startedAt);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("对话处理失败，请稍后重试", summary, trace));
        }
    }

    @GetMapping("/actions")
    public ResponseEntity<ActionHistoryResponse> actionHistory(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(ActionHistoryResponse.error("请先登录"));
        }
        try {
            return ResponseEntity.ok(ActionHistoryResponse.success(actionService.history(userId, limit)));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ActionHistoryResponse.error("操作记录暂时无法读取"));
        }
    }

    @PostMapping("/actions/{token}/confirm")
    public ResponseEntity<ActionResponse> confirmAction(
            @PathVariable String token,
            HttpServletRequest request) {
        return handleAction(token, request, true);
    }

    @PostMapping("/actions/{token}/cancel")
    public ResponseEntity<ActionResponse> cancelAction(
            @PathVariable String token,
            HttpServletRequest request) {
        return handleAction(token, request, false);
    }

    private ResponseEntity<ActionResponse> handleAction(
            String token, HttpServletRequest request, boolean confirm) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(401).body(ActionResponse.error("请先登录"));
        }
        try {
            ActionExecution result = confirm
                    ? actionService.confirm(userId, token)
                    : actionService.cancel(userId, token);
            return ResponseEntity.ok(new ActionResponse(
                    result.success(), result.success() ? result.message() : null,
                    result.success() ? null : result.message(), result.action()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ActionResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ActionResponse.error("操作处理失败，请稍后重试"));
        }
    }

    List<Map<String, Object>> buildMessages(
            List<ChatMessage> history, List<ValidatedAttachment> attachments) {
        int totalCharacters = history.stream().mapToInt(item -> item.content().length()).sum();
        if (totalCharacters > MAX_TOTAL_MESSAGE_CHARS) {
            throw new IllegalArgumentException("消息历史总长度不能超过20000字");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptTemplateService.renderAgent(
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")))));

        int attachmentMessageIndex = -1;
        if (!attachments.isEmpty()) {
            for (int i = history.size() - 1; i >= 0; i--) {
                if ("user".equals(history.get(i).role())) {
                    attachmentMessageIndex = i;
                    break;
                }
            }
            if (attachmentMessageIndex < 0) {
                throw new IllegalArgumentException("图片必须随用户消息一起发送");
            }
        }

        for (int i = 0; i < history.size(); i++) {
            ChatMessage item = history.get(i);
            if (i == attachmentMessageIndex) {
                List<Map<String, Object>> content = new ArrayList<>();
                content.add(Map.of("type", "text", "text", safetyService.redactForModel(item.content().trim())));
                for (ValidatedAttachment attachment : attachments) {
                    content.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", attachment.dataUrl())));
                }
                messages.add(Map.of("role", item.role(), "content", content));
            } else {
                messages.add(Map.of("role", item.role(), "content",
                        safetyService.redactForModel(item.content().trim())));
            }
        }
        return messages;
    }

    private List<ValidatedAttachment> validateAttachments(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        int totalBytes = 0;
        List<ValidatedAttachment> result = new ArrayList<>();
        for (ChatAttachment attachment : attachments) {
            String mimeType = attachment.mimeType().trim().toLowerCase();
            if (!IMAGE_TYPES.contains(mimeType)) {
                throw new IllegalArgumentException("仅支持 JPG、PNG、WebP 图片");
            }
            String prefix = "data:" + mimeType + ";base64,";
            if (!attachment.dataUrl().startsWith(prefix)) {
                throw new IllegalArgumentException("图片编码与文件类型不一致");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(attachment.dataUrl().substring(prefix.length()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("图片编码无效");
            }
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("单张图片大小必须在 4MB 以内");
            }
            if (!hasValidImageSignature(mimeType, bytes)) {
                throw new IllegalArgumentException("图片实际格式与声明类型不一致");
            }
            totalBytes += bytes.length;
            if (totalBytes > MAX_TOTAL_IMAGE_BYTES) {
                throw new IllegalArgumentException("本次图片总大小不能超过 8MB");
            }
            result.add(new ValidatedAttachment(attachment.name().trim(), mimeType, attachment.dataUrl()));
        }
        return result;
    }

    boolean hasValidImageSignature(String mimeType, byte[] bytes) {
        if (bytes == null) return false;
        return switch (mimeType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private ToolDispatch executeToolCalls(
            List<?> toolCalls, List<Map<String, Object>> messages, Integer userId,
            List<TraceStep> trace) {
        int calls = 0;
        int successes = 0;
        int failures = 0;
        for (Object value : toolCalls) {
            calls++;
            if (!(value instanceof Map<?, ?> toolCall)
                    || !(toolCall.get("id") instanceof String toolCallId)
                    || !(toolCall.get("function") instanceof Map<?, ?> function)
                    || !(function.get("name") instanceof String toolName)) {
                failures++;
                throw new IllegalArgumentException("AI 工具调用格式无效");
            }

            String rawArguments = function.get("arguments") instanceof String text ? text : "{}";
            Map<String, Object> arguments;
            try {
                arguments = jsonMapper.readValue(rawArguments, ARGUMENT_TYPE);
            } catch (Exception ex) {
                failures++;
                messages.add(toolMessage(toolCallId, "参数格式无效，请重新调用工具"));
                trace.add(step("TOOL:" + toolName, toolName, "failed", "参数格式未通过校验"));
                continue;
            }

            ToolExecutor.ToolPlan plan;
            try {
                plan = toolExecutor.prepare(toolName, arguments, userId);
            } catch (IllegalArgumentException ex) {
                failures++;
                messages.add(toolMessage(toolCallId, "无法执行：" + ex.getMessage()));
                trace.add(step("TOOL:" + toolName, toolName, "failed", ex.getMessage()));
                continue;
            }

            if (plan.requiresConfirmation()) {
                successes++;
                trace.add(step("TOOL:" + toolName, plan.summary(), "waiting", "已生成待确认动作，业务数据未修改"));
                return new ToolDispatch(actionService.createPending(userId, plan), calls, successes, failures);
            }

            ToolExecutor.ToolResult result = toolExecutor.execute(plan, userId);
            actionService.recordImmediate(userId, plan, result);
            messages.add(toolMessage(toolCallId, result.content()));
            if (result.success()) successes++; else failures++;
            trace.add(step("TOOL:" + toolName, plan.summary(), result.success() ? "completed" : "failed",
                    result.success() ? "真实数据读取完成" : "工具返回失败"));
        }
        return new ToolDispatch(null, calls, successes, failures);
    }

    private RunSummary recordRun(
            String runId, Integer userId, ProviderSelection provider, String modality,
            String status, long startedNanos, int llmCalls, int toolCalls,
            int successfulTools, int failedTools, boolean confirmationRequired,
            String errorType, String promptVersion, TokenUsage usage, LocalDateTime startedAt) {
        long latencyMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        String providerId = provider == null ? "unresolved" : provider.id();
        String model = provider == null ? "unresolved" : provider.model();
        runService.record(new AgentRunService.RunRecord(
                runId, userId, providerId, model, modality, status, latencyMs,
                llmCalls, toolCalls, successfulTools, failedTools,
                confirmationRequired, errorType, promptVersion,
                usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), startedAt));
        return new RunSummary(runId, providerId, model, modality, status, latencyMs,
                llmCalls, toolCalls, confirmationRequired, usage.totalTokens(), promptVersion);
    }

    private TraceStep step(String code, String title, String status, String detail) {
        return new TraceStep(code, title, status, detail, LocalDateTime.now());
    }

    private Map<String, Object> toolMessage(String toolCallId, String content) {
        return Map.of("role", "tool", "tool_call_id", toolCallId, "content", content);
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object currentUser = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        return currentUser instanceof Integer userId ? userId : null;
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String localKnowledgeFallback(ChatRequest requestBody, AiProviderException exception) {
        if (exception.getHttpStatus() < 500
                || requestBody.attachments() != null && !requestBody.attachments().isEmpty()) {
            return null;
        }
        String query = requestBody.messages().stream()
                .filter(message -> "user".equals(message.role()))
                .reduce((first, second) -> second)
                .map(ChatMessage::content)
                .orElse("");
        String normalized = query.toLowerCase();
        boolean knowledgeIntent = normalized.matches("(?s).*(知识|sop|安全|注意|流程|怎么|如何|照护|健康科普|能力边界).*" );
        boolean writeIntent = normalized.matches("(?s).*(预约|报名|取消|下单|预订|执行).*" );
        if (!knowledgeIntent || writeIntent) return null;
        String result = knowledgeBaseService.searchText(query, 3);
        if (!result.startsWith("已从受控知识库检索到")) return null;
        return "外部模型暂时不可用。以下为 SilverPilot 本地受控知识库的只读检索结果，未经模型改写：\n\n"
                + result;
    }

    private boolean isFakeSuccessReply(String reply) {
        String normalized = reply.toLowerCase();
        return normalized.contains("预约成功")
                || normalized.contains("报名成功")
                || normalized.contains("取消成功")
                || normalized.contains("订单待受理")
                || normalized.contains("已成功")
                || normalized.contains("已经为您")
                || normalized.contains("已完成")
                || normalized.contains("已报名")
                || normalized.contains("已预约")
                || normalized.contains("已取消")
                || normalized.contains("successfully");
    }

    private String cleanReply(String reply) {
        return reply
                .replaceAll("(?s)<｜DSML｜[^>]*>.*?</｜DSML｜>", "")
                .replaceAll("</?｜DSML｜[^>]*>", "")
                .trim();
    }

    private record ValidatedAttachment(String name, String mimeType, String dataUrl) { }
    private record ToolDispatch(ActionView pendingAction, int toolCalls, int successfulTools, int failedTools) { }

    public record ChatRequest(
            @NotEmpty(message = "消息不能为空")
            @Size(max = 30, message = "消息历史最多保留30条")
            List<@Valid ChatMessage> messages,
            @Pattern(regexp = "auto|deepseek|doubao|mock", message = "模型提供方无效")
            String provider,
            @Size(max = 3, message = "一次最多上传3张图片")
            List<@Valid ChatAttachment> attachments) { }

    public record CarePlanRequest(
            @NotBlank(message = "照护需求不能为空")
            @Size(max = 1200, message = "照护需求不能超过1200字")
            String needs,
            @Pattern(regexp = "auto|deepseek|doubao|mock", message = "模型提供方无效")
            String provider) { }

    public record ChatMessage(
            @NotBlank(message = "消息角色不能为空")
            @Pattern(regexp = "user|assistant", message = "消息角色无效")
            String role,
            @NotBlank(message = "消息内容不能为空")
            @Size(max = 4000, message = "单条消息不能超过4000字")
            String content) { }

    public record ChatAttachment(
            @NotBlank(message = "图片名称不能为空")
            @Size(max = 120, message = "图片名称过长")
            String name,
            @NotBlank(message = "图片类型不能为空")
            String mimeType,
            @NotBlank(message = "图片内容不能为空")
            @Size(max = 5_700_000, message = "图片内容过大")
            String dataUrl) { }

    public record TraceStep(
            String code, String title, String status, String detail, LocalDateTime timestamp) { }

    public record RunSummary(
            String runId, String provider, String model, String inputModality, String status,
            long latencyMs, int llmCalls, int toolCalls, boolean confirmationRequired,
            int totalTokens, String promptVersion) { }

    public record ChatResponse(
            boolean success, String reply, String error, ActionView pendingAction,
            RunSummary run, List<TraceStep> trace) {
        public static ChatResponse success(String reply, RunSummary run, List<TraceStep> trace) {
            return new ChatResponse(true, reply, null, null, run, List.copyOf(trace));
        }

        public static ChatResponse pending(
                String reply, ActionView pendingAction, RunSummary run, List<TraceStep> trace) {
            return new ChatResponse(true, reply, null, pendingAction, run, List.copyOf(trace));
        }

        public static ChatResponse error(String error, RunSummary run, List<TraceStep> trace) {
            return new ChatResponse(false, null, error, null, run, List.copyOf(trace));
        }
    }

    public record ActionResponse(boolean success, String message, String error, ActionView action) {
        public static ActionResponse error(String error) {
            return new ActionResponse(false, null, error, null);
        }
    }

    public record ActionHistoryResponse(boolean success, List<ActionView> actions, String error) {
        public static ActionHistoryResponse success(List<ActionView> actions) {
            return new ActionHistoryResponse(true, actions, null);
        }

        public static ActionHistoryResponse error(String error) {
            return new ActionHistoryResponse(false, List.of(), error);
        }
    }
}
