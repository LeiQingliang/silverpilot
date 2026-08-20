package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentActionService;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.service.AiProviderService;
import com.cecsmsserve.service.KnowledgeBaseService;
import com.cecsmsserve.service.ToolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless MCP Streamable HTTP endpoint for WorkBuddy. Only non-personal,
 * read-only tools are exposed; business mutations remain behind the web UI's
 * authenticated confirmation flow.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    public static final String PROTOCOL_VERSION = "2025-03-26";
    public static final List<String> PUBLIC_TOOL_NAMES = List.of(
            "cecsms_get_platform_overview",
            "cecsms_list_activities",
            "cecsms_list_services",
            "cecsms_list_recipes",
            "cecsms_search_knowledge",
            "cecsms_get_agent_metrics");
    private static final int SYSTEM_USER = -1;

    private final String apiKey;
    private final ToolExecutor toolExecutor;
    private final AgentActionService actionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AgentRunService runService;
    private final AiProviderService providerService;
    private final JsonMapper jsonMapper;

    public McpController(
            @Value("${agent.mcp.api-key:}") String apiKey,
            ToolExecutor toolExecutor,
            AgentActionService actionService,
            KnowledgeBaseService knowledgeBaseService,
            AgentRunService runService,
            AiProviderService providerService,
            JsonMapper jsonMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.toolExecutor = toolExecutor;
        this.actionService = actionService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.runService = runService;
        this.providerService = providerService;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handle(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-CECSMS-MCP-Key", required = false) String directKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Object id = request.get("id");
        ResponseEntity<Object> authentication = authenticate(id, directKey, authorization);
        if (authentication != null) return authentication;

        String method = request.get("method") instanceof String value ? value : "";
        if (!"2.0".equals(request.get("jsonrpc")) || method.isBlank()) {
            return jsonError(HttpStatus.OK, id, -32600, "Invalid Request");
        }
        if (id == null && method.startsWith("notifications/")) {
            return ResponseEntity.accepted().build();
        }
        try {
            Object result = switch (method) {
                case "initialize" -> initialize(request);
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools", toolDefinitions());
                case "tools/call" -> callTool(request);
                default -> null;
            };
            if (result == null) {
                return jsonError(HttpStatus.OK, id, -32601, "Method not found");
            }
            return ResponseEntity.ok(jsonResult(id, result));
        } catch (IllegalArgumentException ex) {
            return jsonError(HttpStatus.OK, id, -32602, ex.getMessage());
        } catch (Exception ex) {
            return jsonError(HttpStatus.OK, id, -32603, "Internal MCP error");
        }
    }

    @GetMapping
    public ResponseEntity<Void> streamNotEnabled(
            @RequestHeader(value = "X-CECSMS-MCP-Key", required = false) String directKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!isAuthorized(directKey, authorization)) {
            return ResponseEntity.status(apiKey.isBlank() ? 503 : 401).build();
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "POST, DELETE")
                .build();
    }

    @DeleteMapping
    public ResponseEntity<Void> closeSession(
            @RequestHeader(value = "X-CECSMS-MCP-Key", required = false) String directKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return isAuthorized(directKey, authorization)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(apiKey.isBlank() ? 503 : 401).build();
    }

    private Object initialize(Map<String, Object> request) {
        return Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
                "serverInfo", Map.of("name", "silverpilot-cecsms", "version", "1.0.0"),
                "instructions", "SilverPilot 智慧养老只读运营连接器。个人健康数据和写操作不会通过 MCP 暴露。");
    }

    @SuppressWarnings("unchecked")
    private Object callTool(Map<String, Object> request) throws Exception {
        Object paramsValue = request.get("params");
        if (!(paramsValue instanceof Map<?, ?> params)) {
            throw new IllegalArgumentException("params is required");
        }
        String name = params.get("name") instanceof String value ? value : "";
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw : Map.of();

        try {
            String text = switch (name) {
                case "cecsms_get_platform_overview" -> platformOverview();
                case "cecsms_list_activities" -> executePublic("list_available_activities", Map.of());
                case "cecsms_list_services" -> executePublic("list_services", Map.of());
                case "cecsms_list_recipes" -> executePublic("list_recipes",
                        optionalArgument(arguments, "keyword", 50));
                case "cecsms_search_knowledge" -> knowledgeBaseService.searchText(
                        requiredString(arguments, "query", 200), 3);
                case "cecsms_get_agent_metrics" -> jsonMapper.writeValueAsString(
                        runService.systemAnalytics(optionalDays(arguments)));
                default -> throw new IllegalArgumentException("Unknown tool: " + name);
            };
            actionService.recordIntegration(name, true, text);
            return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false);
        } catch (IllegalArgumentException ex) {
            actionService.recordIntegration(name, false, ex.getMessage());
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", ex.getMessage())),
                    "isError", true);
        } catch (Exception ex) {
            actionService.recordIntegration(name, false, "Internal MCP tool error");
            throw ex;
        }
    }

    private String platformOverview() throws Exception {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("product", "SilverPilot 智慧养老业务 Agent 平台");
        overview.put("businessTools", 13);
        overview.put("writePolicy", "Web 端 JWT 身份校验 + 人工二次确认；MCP 不暴露写操作");
        overview.put("knowledge", knowledgeBaseService.status());
        overview.put("providers", providerService.statuses());
        return jsonMapper.writeValueAsString(overview);
    }

    private String executePublic(String toolName, Map<String, Object> arguments) {
        ToolExecutor.ToolPlan plan = toolExecutor.prepare(toolName, arguments, SYSTEM_USER);
        if (plan.requiresConfirmation()) {
            throw new IllegalArgumentException("MCP does not expose mutating tools");
        }
        ToolExecutor.ToolResult result = toolExecutor.execute(plan, SYSTEM_USER);
        if (!result.success()) throw new IllegalArgumentException(result.content());
        return result.content();
    }

    private List<Map<String, Object>> toolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(mcpTool("cecsms_get_platform_overview", "读取平台能力、模型和知识库状态", schema(Map.of(), List.of())));
        tools.add(mcpTool("cecsms_list_activities", "列出当前可报名养老活动及 ID", schema(Map.of(), List.of())));
        tools.add(mcpTool("cecsms_list_services", "列出当前可预约养老服务及 ID", schema(Map.of(), List.of())));
        tools.add(mcpTool("cecsms_list_recipes", "列出或搜索健康菜谱", schema(
                Map.of("keyword", property("string", "可选关键词，最长50字")), List.of())));
        tools.add(mcpTool("cecsms_search_knowledge", "检索已审核、可追溯的养老业务知识", schema(
                Map.of("query", property("string", "检索问题，最长200字")), List.of("query"))));
        tools.add(mcpTool("cecsms_get_agent_metrics", "读取最近1到30天的匿名聚合 Agent 运营指标", schema(
                Map.of("days", property("integer", "统计天数，1到30")), List.of())));
        return tools;
    }

    private Map<String, Object> mcpTool(String name, String description, Map<String, Object> schema) {
        return Map.of(
                "name", name,
                "title", name.replace("cecsms_", "SilverPilot "),
                "description", description,
                "inputSchema", schema,
                "annotations", Map.of(
                        "readOnlyHint", true,
                        "destructiveHint", false,
                        "idempotentHint", true,
                        "openWorldHint", false));
    }

    private Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required,
                "additionalProperties", false);
    }

    private Map<String, Object> property(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private Map<String, Object> optionalArgument(Map<String, Object> arguments, String name, int maxLength) {
        Object raw = arguments.get(name);
        if (raw == null || raw.toString().isBlank()) return Map.of();
        String value = raw.toString().trim();
        if (value.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return Map.of(name, value);
    }

    private String requiredString(Map<String, Object> arguments, String name, int maxLength) {
        Object raw = arguments.get(name);
        if (raw == null || raw.toString().isBlank()) throw new IllegalArgumentException(name + " is required");
        String value = raw.toString().trim();
        if (value.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return value;
    }

    private int optionalDays(Map<String, Object> arguments) {
        Object raw = arguments.get("days");
        if (raw == null) return 7;
        try {
            int days = raw instanceof Number number ? number.intValue() : Integer.parseInt(raw.toString());
            if (days < 1 || days > 30) throw new NumberFormatException();
            return days;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("days must be between 1 and 30");
        }
    }

    private ResponseEntity<Object> authenticate(Object id, String directKey, String authorization) {
        if (apiKey.isBlank()) {
            return jsonError(HttpStatus.SERVICE_UNAVAILABLE, id, -32000,
                    "MCP is disabled until CECSMS_MCP_API_KEY is configured");
        }
        if (!isAuthorized(directKey, authorization)) {
            return jsonError(HttpStatus.UNAUTHORIZED, id, -32001, "Unauthorized");
        }
        return null;
    }

    private boolean isAuthorized(String directKey, String authorization) {
        if (apiKey.isBlank()) return false;
        String supplied = directKey;
        if ((supplied == null || supplied.isBlank()) && authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            supplied = authorization.substring(7).trim();
        }
        if (supplied == null) return false;
        return MessageDigest.isEqual(apiKey.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> jsonResult(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private ResponseEntity<Object> jsonError(
            HttpStatus status, Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.status(status).body(response);
    }
}
