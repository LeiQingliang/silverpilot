package com.cecsmsserve.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Operations records contain classifications and evidence identifiers, never conversation text. */
@Service
public class AgentFeedbackService {
    private static final Set<String> CATEGORIES = Set.of(
            "KNOWLEDGE_GAP", "CONFUSING_RESPONSE", "TOOL_ERROR", "CONFIRMATION_UX", "MODEL_PLANNING");
    private static final Set<String> PRIORITIES = Set.of("P1", "P2", "P3");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "OPEN", Set.of("TRIAGED"), "TRIAGED", Set.of("FIXED"),
            "FIXED", Set.of("VERIFIED", "TRIAGED"), "VERIFIED", Set.of("TRIAGED"));
    private final JdbcTemplate jdbc;

    public AgentFeedbackService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public FeedbackView create(String runId, String category, String priority, int actorId) {
        if (runId == null || !runId.matches("[a-f0-9]{32}")) throw invalid("请选择有效的运行记录");
        if (!CATEGORIES.contains(value(category)) || !PRIORITIES.contains(value(priority))) {
            throw invalid("问题类别或优先级无效");
        }
        Long runs = jdbc.queryForObject("SELECT COUNT(*) FROM agent_run WHERE run_id = ?", Long.class, runId);
        if (runs == null || runs == 0) throw invalid("关联运行记录不存在");
        String id = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO agent_feedback
                (feedback_id, run_id, category, priority, status, owner_id, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?, 0, ?, ?)
                """, id, runId, category, priority, actorId, now, now);
        event(id, null, "OPEN", actorId, null, null, now);
        return require(id);
    }

    @Transactional
    public FeedbackView transition(String id, int version, String status, String changeRef,
                                   String evidenceRef, boolean verificationPassed, int actorId) {
        FeedbackView current = require(id);
        if (version < 0 || current.version() != version) throw new Conflict("记录已更新，请刷新后重试");
        if (!TRANSITIONS.getOrDefault(current.status(), Set.of()).contains(value(status))) {
            throw invalid("状态流转无效：请先分诊、提交修复，再记录复测");
        }
        String change = normalize(changeRef);
        String evidence = normalize(evidenceRef);
        if ("FIXED".equals(status) || "VERIFIED".equals(status)) {
            if (change == null) change = current.changeRef();
            if (change == null || !change.matches("(commit:[a-f0-9]{7,40}|issue:[1-9][0-9]{0,9})")) {
                throw invalid("请提供修复引用：commit:提交哈希 或 issue:编号");
            }
        } else {
            change = null;
        }
        if ("VERIFIED".equals(status)) {
            if (!verificationPassed) throw invalid("请确认已经人工复测通过");
            if (evidence == null || !evidence.matches("eval:[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
                throw invalid("关闭问题前必须提供复测记录编号，例如 eval:case-001");
            }
        } else {
            evidence = null;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE agent_feedback SET status = ?, owner_id = ?, change_ref = ?, evidence_ref = ?,
                    version = version + 1, updated_at = ?, verified_at = ?
                WHERE feedback_id = ? AND version = ?
                """, status, actorId, change, evidence, now, "VERIFIED".equals(status) ? now : null, id, version);
        if (updated != 1) throw new Conflict("记录已更新，请刷新后重试");
        event(id, current.status(), status, actorId, change, evidence, now);
        return require(id);
    }

    public FeedbackPage page(int requestedDays) {
        int days = days(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<FeedbackView> items = jdbc.query("""
                SELECT * FROM agent_feedback WHERE created_at >= ?
                ORDER BY created_at DESC, feedback_id DESC LIMIT 101
                """, (row, index) -> new FeedbackView(row.getString("feedback_id"), row.getString("run_id"),
                row.getString("category"), row.getString("priority"), row.getString("status"),
                row.getInt("owner_id"), row.getInt("version"), row.getString("change_ref"),
                row.getString("evidence_ref"), row.getTimestamp("created_at").toLocalDateTime(),
                row.getTimestamp("updated_at").toLocalDateTime()), since);
        return new FeedbackPage(days, items.size() > 100, items.stream().limit(100).toList());
    }

    public Map<String, Object> summary(int requestedDays) {
        int days = days(requestedDays);
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, Long> states = new LinkedHashMap<>();
        Map<String, Long> categories = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS n FROM agent_feedback WHERE created_at >= ? GROUP BY status",
                row -> { states.put(row.getString("status"), row.getLong("n")); }, since);
        jdbc.query("SELECT category, COUNT(*) AS n FROM agent_feedback WHERE created_at >= ? GROUP BY category",
                row -> { categories.put(row.getString("category"), row.getLong("n")); }, since);
        long total = states.values().stream().mapToLong(Long::longValue).sum();
        long verified = states.getOrDefault("VERIFIED", 0L);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("total", total);
        result.put("verified", verified);
        result.put("verifiedClosureRate", total == 0 ? null : Math.round(1000.0 * verified / total) / 10.0);
        result.put("statuses", states);
        result.put("categories", categories);
        result.put("definition", "按创建时间选取同一批问题，以当前已验证状态计算；新问题观察期尚未成熟。");
        return result;
    }

    /** Confirmation outcomes are a separate cohort, not a model-attributed conversion funnel. */
    public Map<String, Object> actionOutcomes(int requestedDays) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> states = new LinkedHashMap<>();
        jdbc.query("""
                SELECT CASE WHEN status = 'PENDING' AND expires_at <= ? THEN 'EXPIRED' ELSE status END AS state,
                    COUNT(*) AS n
                FROM agent_action WHERE requires_confirmation = 1 AND created_at >= ?
                GROUP BY CASE WHEN status = 'PENDING' AND expires_at <= ? THEN 'EXPIRED' ELSE status END
                """, row -> { states.put(row.getString("state"), row.getLong("n")); },
                now, now.minusDays(days(requestedDays)), now);
        long total = states.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("statuses", states);
        result.put("definition", "按待确认动作创建时间选取；SUCCEEDED 才表示执行成功。未关联运行，不能按模型归因。");
        return result;
    }

    private FeedbackView require(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw invalid("问题编号无效");
        List<FeedbackView> rows = jdbc.query("SELECT * FROM agent_feedback WHERE feedback_id = ?",
                (row, index) -> new FeedbackView(row.getString("feedback_id"), row.getString("run_id"),
                        row.getString("category"), row.getString("priority"), row.getString("status"),
                        row.getInt("owner_id"), row.getInt("version"), row.getString("change_ref"),
                        row.getString("evidence_ref"), row.getTimestamp("created_at").toLocalDateTime(),
                        row.getTimestamp("updated_at").toLocalDateTime()), id);
        if (rows.isEmpty()) throw invalid("问题记录不存在");
        return rows.getFirst();
    }

    private void event(String id, String from, String to, int actor, String change, String evidence, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO agent_feedback_event
                (feedback_id, from_status, to_status, actor_id, change_ref, evidence_ref, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, from, to, actor, change, evidence, now);
    }
    private int days(int value) { return Math.max(1, Math.min(value, 30)); }
    private String value(String value) { return value == null ? "" : value; }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    public static class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public Conflict(String message) { super(message); }
    }
    public record FeedbackView(String id, String runId, String category, String priority, String status,
                               int ownerId, int version, String changeRef, String evidenceRef,
                               LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record FeedbackPage(int days, boolean truncated, List<FeedbackView> items) { }
}
