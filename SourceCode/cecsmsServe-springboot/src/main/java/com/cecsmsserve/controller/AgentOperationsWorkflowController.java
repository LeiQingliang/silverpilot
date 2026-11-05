package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentFeedbackService;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.util.JWTInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/chat/admin/operations")
public class AgentOperationsWorkflowController {
    private final AgentFeedbackService feedback;
    private final AgentRunService runs;

    public AgentOperationsWorkflowController(AgentFeedbackService feedback, AgentRunService runs) {
        this.feedback = feedback;
        this.runs = runs;
    }

    @GetMapping
    public ResponseEntity<?> overview(@RequestParam(defaultValue = "7") int days, HttpServletRequest request) {
        ResponseEntity<?> denied = authorize(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of("feedback", feedback.page(days), "feedbackSummary", feedback.summary(days),
                "actionOutcomes", feedback.actionOutcomes(days)));
    }

    /** This is the only downloadable operations report; no user, run or feedback identifiers. */
    @GetMapping("/export")
    public ResponseEntity<?> export(@RequestParam(defaultValue = "7") int days, HttpServletRequest request) {
        ResponseEntity<?> denied = authorize(request);
        if (denied != null) return denied;
        AgentRunService.AnalyticsView analytics = runs.systemAnalytics(days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "silverpilot-operations-v1");
        result.put("generatedAt", LocalDateTime.now());
        result.put("days", analytics.days());
        result.put("totalRuns", analytics.totalRuns());
        result.put("runSuccessRate", analytics.successRate());
        result.put("runSuccessDefinition", "(COMPLETED + WAITING_CONFIRMATION) / sampled runs; not business completion");
        result.put("completedRuns", analytics.completedRuns());
        result.put("waitingConfirmationRuns", analytics.waitingConfirmation());
        result.put("degradedRuns", analytics.degradedRuns());
        result.put("failedRuns", analytics.failedRuns());
        result.put("p50LatencyMs", analytics.p50LatencyMs());
        result.put("p95LatencyMs", analytics.p95LatencyMs());
        result.put("totalTokens", analytics.totalTokens());
        result.put("truncated", analytics.truncated());
        result.put("sampleLimit", analytics.sampleLimit());
        result.put("providerMetrics", analytics.providerMetrics());
        result.put("feedbackSummary", feedback.summary(days));
        result.put("actionOutcomes", feedback.actionOutcomes(days));
        result.put("limitations", "Runs, write actions and feedback use separate creation-time cohorts; no model attribution or causal growth claim.");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> create(@Valid @RequestBody CreateFeedback body, HttpServletRequest request) {
        ResponseEntity<?> denied = authorize(request);
        if (denied != null) return denied;
        try {
            return ResponseEntity.status(201).body(feedback.create(body.runId(), body.category(), body.priority(), actor(request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/feedback/{id}/transition")
    public ResponseEntity<?> transition(@PathVariable String id, @Valid @RequestBody Transition body,
                                       HttpServletRequest request) {
        ResponseEntity<?> denied = authorize(request);
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(feedback.transition(id, body.version(), body.status(),
                    body.changeRef(), body.evidenceRef(), body.verificationPassed(), actor(request)));
        } catch (AgentFeedbackService.Conflict ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private ResponseEntity<?> authorize(HttpServletRequest request) {
        Object role = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        Object id = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        if (!(role instanceof Number) || !(id instanceof Number number) || number.intValue() <= 0) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        if (((Number) role).intValue() != 1) {
            return ResponseEntity.status(403).body(Map.of("error", "仅系统管理员可管理运营问题"));
        }
        return null;
    }
    private int actor(HttpServletRequest request) {
        return ((Number) request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE)).intValue();
    }
    public record CreateFeedback(@NotBlank @Size(max = 32) String runId,
                                 @NotBlank @Size(max = 32) String category,
                                 @NotBlank @Size(max = 2) String priority) { }
    public record Transition(@PositiveOrZero int version, @NotBlank @Size(max = 20) String status,
                             @Size(max = 80) String changeRef, @Size(max = 85) String evidenceRef,
                             boolean verificationPassed) { }
}
