package com.cecsmsserve.util.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseContractHealthIndicatorTests {

    private JdbcTemplate jdbcTemplate;
    private DatabaseContractHealthIndicator indicator;
    private Map<String, Object> metrics;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        indicator = new DatabaseContractHealthIndicator(jdbcTemplate);
        metrics = new HashMap<>();
        metrics.put("orphan_rows", 0L);
        metrics.put("invalid_registration_states", 0L);
        metrics.put("registration_count_mismatches", 0L);
        metrics.put("invalid_service_hierarchy", 0L);
        when(jdbcTemplate.queryForObject(
                DatabaseContractHealthIndicator.TABLE_COUNT_SQL, Integer.class))
                .thenReturn(DatabaseContractHealthIndicator.REQUIRED_TABLE_COUNT);
        when(jdbcTemplate.queryForObject(
                DatabaseContractHealthIndicator.INDEX_COUNT_SQL, Integer.class))
                .thenReturn(DatabaseContractHealthIndicator.REQUIRED_INDEX_COUNT);
        when(jdbcTemplate.queryForObject(
                DatabaseContractHealthIndicator.FOREIGN_KEY_COUNT_SQL, Integer.class))
                .thenReturn(DatabaseContractHealthIndicator.REQUIRED_FOREIGN_KEY_COUNT);
        when(jdbcTemplate.queryForMap(DatabaseContractHealthIndicator.DATA_CONTRACT_SQL))
                .thenReturn(metrics);
    }

    @Test
    void reportsUpOnlyWhenSchemaIndexesAndDataAreConsistent() {
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(0L, health.getDetails().get("orphanRows"));
    }

    @Test
    void reportsDownWhenAnActiveRelationContainsOrphans() {
        metrics.put("orphan_rows", 1L);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(1L, health.getDetails().get("orphanRows"));
    }

    @Test
    void reportsDownWhenTheRequiredSchemaIsIncomplete() {
        when(jdbcTemplate.queryForObject(
                DatabaseContractHealthIndicator.TABLE_COUNT_SQL, Integer.class))
                .thenReturn(DatabaseContractHealthIndicator.REQUIRED_TABLE_COUNT - 1);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}
