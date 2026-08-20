package com.cecsmsserve.util.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("databaseContract")
@ConditionalOnProperty(
        name = "app.database-contract.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DatabaseContractHealthIndicator extends AbstractHealthIndicator {

    static final int REQUIRED_TABLE_COUNT = 21;
    static final int REQUIRED_INDEX_COUNT = 6;
    static final int REQUIRED_FOREIGN_KEY_COUNT = 18;

    static final String TABLE_COUNT_SQL = """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_type = 'BASE TABLE'
              AND table_name IN (
                'activity','activity_state','activity_type','agent_action','agent_run',
                'comment','emergency_help','message','message_receiver','news','order_state',
                'recipe','recipe_order','report','role','role_function','service_order',
                'service_type','sys_function','user','user_activity'
              )
            """;

    static final String INDEX_COUNT_SQL = """
            SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name))
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND CONCAT(table_name, ':', index_name) IN (
                'activity:idx_activity_state_schedule',
                'user_activity:idx_user_activity_activity_state',
                'user_activity:idx_user_activity_user_state',
                'service_order:idx_service_order_user_state',
                'service_order:idx_service_order_type_state',
                'report:idx_report_user_time'
              )
            """;

    static final String FOREIGN_KEY_COUNT_SQL = """
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE()
              AND constraint_type = 'FOREIGN KEY'
              AND constraint_name IN (
                'fk_user_role','fk_activity_type','fk_activity_director',
                'fk_user_activity_user','fk_user_activity_activity','fk_service_type_parent',
                'fk_service_order_user','fk_service_order_worker','fk_service_order_doctor',
                'fk_service_order_parent_type','fk_service_order_child_type',
                'fk_report_user','fk_report_doctor','fk_comment_user','fk_comment_reply_user',
                'fk_comment_parent','fk_role_function_role','fk_role_function_function'
              )
            """;

    static final String DATA_CONTRACT_SQL = """
            SELECT
              (
                (SELECT COUNT(*) FROM activity a LEFT JOIN activity_type t ON t.id=a.activityTypeId
                  WHERE a.activityTypeId IS NOT NULL AND t.id IS NULL) +
                (SELECT COUNT(*) FROM activity a LEFT JOIN user u ON u.id=a.dId
                  WHERE a.dId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM user_activity x LEFT JOIN user u ON u.id=x.uId
                  WHERE x.uId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM user_activity x LEFT JOIN activity a ON a.id=x.aId
                  WHERE x.aId IS NOT NULL AND a.id IS NULL) +
                (SELECT COUNT(*) FROM service_type c LEFT JOIN service_type p ON p.id=c.leaderId
                  WHERE c.leaderId IS NOT NULL AND p.id IS NULL) +
                (SELECT COUNT(*) FROM service_order o LEFT JOIN user u ON u.id=o.uId
                  WHERE o.uId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM service_order o LEFT JOIN user u ON u.id=o.mId
                  WHERE o.mId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM service_order o LEFT JOIN user u ON u.id=o.dId
                  WHERE o.dId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM service_order o LEFT JOIN service_type t ON t.id=o.typeBId
                  WHERE o.typeBId IS NOT NULL AND t.id IS NULL) +
                (SELECT COUNT(*) FROM service_order o LEFT JOIN service_type t ON t.id=o.typeSId
                  WHERE o.typeSId IS NOT NULL AND t.id IS NULL) +
                (SELECT COUNT(*) FROM report r LEFT JOIN user u ON u.id=r.uId
                  WHERE r.uId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM report r LEFT JOIN user u ON u.id=r.dId
                  WHERE r.dId IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM comment c LEFT JOIN user u ON u.id=c.user_id
                  WHERE c.user_id IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM comment c LEFT JOIN user u ON u.id=c.reply_to
                  WHERE c.reply_to IS NOT NULL AND u.id IS NULL) +
                (SELECT COUNT(*) FROM comment c LEFT JOIN comment p ON p.id=c.parent_id
                  WHERE c.parent_id IS NOT NULL AND p.id IS NULL) +
                (SELECT COUNT(*) FROM role_function rf LEFT JOIN role r ON r.id=rf.rId
                  WHERE r.id IS NULL) +
                (SELECT COUNT(*) FROM role_function rf LEFT JOIN sys_function f ON f.id=rf.fId
                  WHERE f.id IS NULL) +
                (SELECT COUNT(*) FROM user u LEFT JOIN role r ON r.id=u.roleId
                  WHERE r.id IS NULL)
              ) AS orphan_rows,
              (SELECT COUNT(*) FROM user_activity
                WHERE state IS NULL OR state NOT IN ('报名成功','报名审核中','已取消报名')) AS invalid_registration_states,
              (SELECT COUNT(*) FROM activity a
                WHERE COALESCE(a.signNum,0) <> (
                  SELECT COUNT(*) FROM user_activity ua
                  WHERE ua.aId=a.id AND ua.state='报名成功'
                )) AS registration_count_mismatches,
              (SELECT COUNT(*)
                FROM service_order o
                JOIN service_type p ON p.id=o.typeBId
                JOIN service_type c ON c.id=o.typeSId
                WHERE p.leaderId IS NOT NULL OR c.leaderId<>p.id) AS invalid_service_hierarchy
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseContractHealthIndicator(JdbcTemplate jdbcTemplate) {
        super("Database contract check failed");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        int tableCount = valueOrZero(jdbcTemplate.queryForObject(TABLE_COUNT_SQL, Integer.class));
        int indexCount = valueOrZero(jdbcTemplate.queryForObject(INDEX_COUNT_SQL, Integer.class));
        int foreignKeyCount = valueOrZero(jdbcTemplate.queryForObject(FOREIGN_KEY_COUNT_SQL, Integer.class));
        Map<String, Object> metrics = jdbcTemplate.queryForMap(DATA_CONTRACT_SQL);

        long orphanRows = number(metrics, "orphan_rows");
        long invalidStates = number(metrics, "invalid_registration_states");
        long countMismatches = number(metrics, "registration_count_mismatches");
        long invalidHierarchy = number(metrics, "invalid_service_hierarchy");
        boolean healthy = tableCount == REQUIRED_TABLE_COUNT
                && indexCount == REQUIRED_INDEX_COUNT
                && foreignKeyCount == REQUIRED_FOREIGN_KEY_COUNT
                && orphanRows == 0
                && invalidStates == 0
                && countMismatches == 0
                && invalidHierarchy == 0;

        builder.status(healthy ? "UP" : "DOWN")
                .withDetail("requiredTables", tableCount + "/" + REQUIRED_TABLE_COUNT)
                .withDetail("criticalIndexes", indexCount + "/" + REQUIRED_INDEX_COUNT)
                .withDetail("activeForeignKeys", foreignKeyCount + "/" + REQUIRED_FOREIGN_KEY_COUNT)
                .withDetail("orphanRows", orphanRows)
                .withDetail("invalidRegistrationStates", invalidStates)
                .withDetail("registrationCountMismatches", countMismatches)
                .withDetail("invalidServiceHierarchy", invalidHierarchy);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : Long.MAX_VALUE;
    }
}
