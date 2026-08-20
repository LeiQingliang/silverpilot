package com.cecsmsserve.util;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSchemaMigrationTests {

    @Test
    void upgradesAnExistingAgentRunTableAndIsIdempotent() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:agent-migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE agent_run (id BIGINT PRIMARY KEY, confirmation_required TINYINT DEFAULT 0)");
        AgentSchemaMigration migration = new AgentSchemaMigration(dataSource);

        migration.run(new DefaultApplicationArguments(new String[0]));
        migration.run(new DefaultApplicationArguments(new String[0]));

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'AGENT_RUN'
                  AND COLUMN_NAME IN ('PROMPT_VERSION','PROMPT_TOKENS','COMPLETION_TOKENS','TOTAL_TOKENS')
                """, Integer.class);
        assertEquals(4, count);
    }
}
