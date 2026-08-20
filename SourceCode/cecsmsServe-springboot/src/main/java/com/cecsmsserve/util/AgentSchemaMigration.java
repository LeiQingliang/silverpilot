package com.cecsmsserve.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.schema.migration.enabled", havingValue = "true", matchIfMissing = true)
public class AgentSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentSchemaMigration.class);
    private static final Map<String, String> COLUMNS = columns();

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public AgentSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        int migrated = 0;
        for (Map.Entry<String, String> entry : COLUMNS.entrySet()) {
            if (columnExists(entry.getKey())) continue;
            try {
                jdbcTemplate.execute("ALTER TABLE agent_run ADD COLUMN " + entry.getKey() + " " + entry.getValue());
                migrated++;
            } catch (DataAccessException ex) {
                if (!columnExists(entry.getKey())) throw ex;
            }
        }
        if (migrated > 0) log.info("Applied {} idempotent agent_run schema upgrades", migrated);
    }

    private boolean columnExists(String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String tableName : new String[] { "agent_run", "AGENT_RUN" }) {
                try (ResultSet columns = metadata.getColumns(catalog, null, tableName, null)) {
                    while (columns.next()) {
                        if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
                    }
                }
            }
            return false;
        }
    }

    private static Map<String, String> columns() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("prompt_version", "VARCHAR(64) NULL");
        result.put("prompt_tokens", "INT NOT NULL DEFAULT 0");
        result.put("completion_tokens", "INT NOT NULL DEFAULT 0");
        result.put("total_tokens", "INT NOT NULL DEFAULT 0");
        return Map.copyOf(result);
    }
}
