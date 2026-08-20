package com.cecsmsserve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.entity.ServiceType;
import com.cecsmsserve.service.AiProviderService.ProviderSelection;
import com.cecsmsserve.service.AiProviderService.TokenUsage;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CareRecommendationService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Set<String> URGENCY = Set.of("low", "medium", "high", "emergency");

    private final IServiceTypeService serviceTypeService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;
    private final AiProviderService providerService;
    private final AiSafetyService safetyService;
    private final AgentRunService runService;
    private final JsonMapper jsonMapper;

    public CareRecommendationService(
            IServiceTypeService serviceTypeService,
            KnowledgeBaseService knowledgeBaseService,
            PromptTemplateService promptTemplateService,
            AiProviderService providerService,
            AiSafetyService safetyService,
            AgentRunService runService,
            JsonMapper jsonMapper) {
        this.serviceTypeService = serviceTypeService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.promptTemplateService = promptTemplateService;
        this.providerService = providerService;
        this.safetyService = safetyService;
        this.runService = runService;
        this.jsonMapper = jsonMapper;
    }

    public CarePlan create(Integer userId, String needs, String requestedProvider) {
        if (userId == null) throw new IllegalArgumentException("用户身份无效");
        String normalizedNeeds = needs == null ? "" : needs.trim();
        if (normalizedNeeds.isEmpty() || normalizedNeeds.length() > 1200) {
            throw new IllegalArgumentException("照护需求需为1到1200字");
        }
        safetyService.assertNoSecrets(List.of(normalizedNeeds));

        String runId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        ProviderSelection provider = null;
        TokenUsage usage = TokenUsage.ZERO;
        String promptVersion = promptTemplateService.metadata(PromptTemplateService.CARE_RECOMMENDATION).version();
        try {
            List<ServiceType> services = activeLeafServices();
            if (services.isEmpty()) throw new IllegalStateException("当前没有可推荐的在用养老服务");
            provider = providerService.resolve(requestedProvider, false);

            if (AiProviderService.MOCK.equals(provider.id())) {
                CarePlan plan = mockPlan(runId, provider, normalizedNeeds, promptVersion);
                record(runId, userId, provider, startedNanos, startedAt, promptVersion, usage,
                        AgentRunService.COMPLETED, null);
                return plan;
            }

            String groundedKnowledge = knowledgeBaseService.searchText(normalizedNeeds, 3);
            String catalogJson = jsonMapper.writeValueAsString(services.stream().map(item -> Map.of(
                    "serviceId", item.getId(),
                    "serviceName", item.getServiceName(),
                    "evidence", "BUSINESS:" + item.getId())).toList());
            List<Map<String, Object>> messages = List.of(
                    Map.of("role", "system", "content", promptTemplateService.renderCareRecommendation(
                            LocalDate.now(BUSINESS_ZONE))),
                    Map.of("role", "user", "content", "请以 json 输出结构化建议。\n用户需求："
                            + safetyService.redactForModel(normalizedNeeds)
                            + "\n真实可用服务：" + catalogJson
                            + "\n已审核知识检索：" + groundedKnowledge));
            Map<String, Object> response = providerService.chatCompletion(provider,
                    providerService.decorateStructuredBody(provider, messages, userId));
            usage = providerService.usage(response);
            Map<String, Object> raw = jsonMapper.readValue(providerService.firstText(response), MAP_TYPE);
            CarePlan plan = validatePlan(runId, provider, promptVersion, usage, raw, services);
            record(runId, userId, provider, startedNanos, startedAt, promptVersion, usage,
                    AgentRunService.COMPLETED, null);
            return plan;
        } catch (AiProviderException | IllegalArgumentException ex) {
            record(runId, userId, provider, startedNanos, startedAt, promptVersion, usage,
                    AgentRunService.FAILED, ex instanceof AiProviderException ? "PROVIDER" : "VALIDATION");
            throw ex;
        } catch (Exception ex) {
            record(runId, userId, provider, startedNanos, startedAt, promptVersion, usage,
                    AgentRunService.FAILED, "STRUCTURED_OUTPUT");
            throw new AiProviderException("结构化照护建议生成失败，请稍后重试", 502, ex);
        }
    }

    private List<ServiceType> activeLeafServices() {
        return serviceTypeService.list(new LambdaQueryWrapper<ServiceType>()
                .eq(ServiceType::getState, 1)
                .isNotNull(ServiceType::getLeaderId)
                .orderByAsc(ServiceType::getId));
    }

    private CarePlan validatePlan(
            String runId,
            ProviderSelection provider,
            String promptVersion,
            TokenUsage usage,
            Map<String, Object> raw,
            List<ServiceType> services) {
        Map<Integer, ServiceType> allowed = new LinkedHashMap<>();
        services.forEach(service -> allowed.put(service.getId(), service));
        String urgency = text(raw.get("urgency"), 20, "low").toLowerCase();
        if (!URGENCY.contains(urgency)) urgency = "medium";

        List<Recommendation> recommendations = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        if (raw.get("recommendations") instanceof List<?> items) {
            for (Object itemValue : items) {
                if (recommendations.size() >= 3 || !(itemValue instanceof Map<?, ?> item)) continue;
                Integer serviceId = integer(item.get("serviceId"));
                ServiceType service = allowed.get(serviceId);
                if (service == null || !seen.add(serviceId)) continue;
                List<String> evidence = evidence(item.get("evidence"), serviceId);
                recommendations.add(new Recommendation(
                        serviceId,
                        service.getServiceName(),
                        text(item.get("reason"), 300, "匹配用户当前描述，建议由工作人员进一步核验"),
                        text(item.get("nextStep"), 240, "查看服务详情，确认时间与地址后再决定是否预约"),
                        evidence));
            }
        }
        if (recommendations.isEmpty() && !"emergency".equals(urgency)) {
            throw new AiProviderException("模型返回的服务建议未通过真实服务ID校验", 502);
        }

        return new CarePlan(
                runId,
                provider.id(),
                provider.model(),
                promptVersion,
                usage.totalTokens(),
                text(raw.get("summary"), 160, "已根据当前描述完成需求整理"),
                urgency,
                List.copyOf(recommendations),
                text(raw.get("safetyNotice"), 300, "请由本人、家属或专业人员核对实际情况"),
                text(raw.get("disclaimer"), 220, "本建议不替代医疗诊断；任何预约仍需用户确认。"),
                LocalDateTime.now());
    }

    private CarePlan mockPlan(String runId, ProviderSelection provider, String needs, String promptVersion) {
        return new CarePlan(
                runId, provider.id(), provider.model(), promptVersion, 0,
                truncate("Mock 模式已接收需求：" + needs, 160),
                "low", List.of(),
                "Mock 模式不进行语义推荐，仅用于验证结构化接口与界面。",
                "请配置真实模型后生成建议；任何预约仍需用户确认。",
                LocalDateTime.now());
    }

    private List<String> evidence(Object value, Integer serviceId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("BUSINESS:" + serviceId);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = String.valueOf(item).trim();
                if (text.matches("KB:[A-Za-z0-9._-]{1,80}")) result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? null : Integer.valueOf(value.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    private String text(Object value, int maxLength, String fallback) {
        String result = value == null ? "" : value.toString().trim();
        return result.isEmpty() ? fallback : truncate(result, maxLength);
    }

    private String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private void record(
            String runId,
            Integer userId,
            ProviderSelection provider,
            long startedNanos,
            LocalDateTime startedAt,
            String promptVersion,
            TokenUsage usage,
            String status,
            String errorType) {
        long latencyMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        runService.record(new AgentRunService.RunRecord(
                runId, userId,
                provider == null ? "unresolved" : provider.id(),
                provider == null ? "unresolved" : provider.model(),
                "STRUCTURED_TEXT", status, latencyMs,
                provider == null ? 0 : 1, 0, 0, 0, false, errorType,
                promptVersion, usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), startedAt));
    }

    public record Recommendation(
            Integer serviceId,
            String serviceName,
            String reason,
            String nextStep,
            List<String> evidence) { }

    public record CarePlan(
            String runId,
            String provider,
            String model,
            String promptVersion,
            int totalTokens,
            String summary,
            String urgency,
            List<Recommendation> recommendations,
            String safetyNotice,
            String disclaimer,
            LocalDateTime generatedAt) { }
}
