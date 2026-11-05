-- MySQL 9.7 read-only diagnostics. Use a read-only account and prepared
-- @window_start/@window_end DATETIME parameters in the database's timezone.
-- Windows are half-open [start,end); these full-period queries do not have
-- the UI's 5000-row sample cap. Never join counts across independent grains.
SELECT provider, COUNT(*) AS runs,
       SUM(status = 'COMPLETED') AS completed,
       SUM(status = 'WAITING_CONFIRMATION') AS waiting_confirmation,
       SUM(status = 'DEGRADED') AS degraded,
       SUM(status = 'FAILED') AS failed,
       ROUND(100.0 * SUM(status IN ('COMPLETED','WAITING_CONFIRMATION'))
             / NULLIF(COUNT(*), 0), 1) AS run_success_pct,
       SUM(prompt_tokens) AS input_tokens,
       SUM(completion_tokens) AS output_tokens
FROM agent_run
WHERE created_at >= @window_start AND created_at < @window_end
GROUP BY provider;

-- Action-created cohort; PENDING is not a successful booking.
SELECT status, COUNT(*) AS actions
FROM agent_action
WHERE requires_confirmation = 1
  AND created_at >= @window_start AND created_at < @window_end
GROUP BY status;

-- Feedback-created cohort, observed at query time, not a response-time SLA.
SELECT category, COUNT(*) AS issues,
       SUM(status = 'VERIFIED') AS verified,
       ROUND(100.0 * SUM(status = 'VERIFIED') / NULLIF(COUNT(*), 0), 1) AS verified_pct
FROM agent_feedback
WHERE created_at >= @window_start AND created_at < @window_end
GROUP BY category;
