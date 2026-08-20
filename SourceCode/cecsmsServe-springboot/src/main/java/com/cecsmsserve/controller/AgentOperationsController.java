package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentActionService;
import com.cecsmsserve.service.AgentRateLimiter;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.service.AiProviderService;
import com.cecsmsserve.service.KnowledgeBaseService;
import com.cecsmsserve.service.PromptTemplateService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.Tools;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Administrator-only, secret-free operational view of Agent and integrations. */
@RestController
@RequestMapping("/chat/admin")
public class AgentOperationsController {

    private static final int ADMIN_ROLE_ID = 1;

    private final AgentRunService runService;
    private final AgentActionService actionService;
    private final AiProviderService providerService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;
    private final AgentRateLimiter rateLimiter;
    private final boolean mcpConfigured;

    public AgentOperationsController(
            AgentRunService runService,
            AgentActionService actionService,
            AiProviderService providerService,
            KnowledgeBaseService knowledgeBaseService,
            PromptTemplateService promptTemplateService,
            AgentRateLimiter rateLimiter,
            @Value("${agent.mcp.api-key:}") String mcpApiKey) {
        this.runService = runService;
        this.actionService = actionService;
        this.providerService = providerService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.promptTemplateService = promptTemplateService;
        this.rateLimiter = rateLimiter;
        this.mcpConfigured = mcpApiKey != null && !mcpApiKey.isBlank();
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        Object role = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        if (!(role instanceof Number number)) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        if (number.intValue() != ADMIN_ROLE_ID) {
            return ResponseEntity.status(403).body(Map.of("error", "仅系统管理员可查看智能运营数据"));
        }

        List<AiProviderService.ProviderView> providers = providerService.statuses();
        if (providers == null) providers = List.of();
        List<AgentActionService.AdminActionView> workBuddy = actionService.integrationHistory(12);
        List<AgentActionService.AdminActionView> knowledgeInteractions =
                actionService.adminHistory("search_knowledge_base", 12);
        List<AgentActionService.AdminActionView> recentActions = actionService.adminHistory(null, 12);

        Map<String, Object> mcp = new LinkedHashMap<>();
        mcp.put("configured", mcpConfigured);
        mcp.put("protocolVersion", McpController.PROTOCOL_VERSION);
        mcp.put("mode", "只读、无个人健康数据、无写操作");
        mcp.put("tools", McpController.PUBLIC_TOOL_NAMES);

        Map<String, Object> multimodal = new LinkedHashMap<>();
        multimodal.put("speech", "浏览器实时语音转写");
        multimodal.put("camera", true);
        multimodal.put("visionConfigured", providers.stream()
                .anyMatch(item -> item.configured() && item.vision()));
        multimodal.put("deepSeekVision", false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        result.put("analytics", runService.systemAnalytics(days));
        result.put("providers", providers);
        result.put("knowledge", knowledgeBaseService.status());
        result.put("knowledgeInteractions", knowledgeInteractions == null ? List.of() : knowledgeInteractions);
        result.put("workBuddy", mcp);
        result.put("workBuddyInteractions", workBuddy == null ? List.of() : workBuddy);
        result.put("recentActions", recentActions == null ? List.of() : recentActions);
        result.put("prompts", promptTemplateService.metadata());
        result.put("rateLimit", rateLimiter.status());
        result.put("multimodal", multimodal);
        result.put("businessToolCount", Tools.getTools().size());
        result.put("confirmationEnabled", true);
        return ResponseEntity.ok(result);
    }
}
