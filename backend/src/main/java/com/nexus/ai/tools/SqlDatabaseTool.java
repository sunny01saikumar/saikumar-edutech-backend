package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SqlDatabaseTool implements Tool {

    private final JdbcTemplate jdbcTemplate;

    public SqlDatabaseTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "sql_database";
    }

    @Override
    public String getDescription() {
        return "Executes read-only or query SQL statements against the application H2 database.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "sql", Map.of("type", "string", "description", "SQL query to execute (e.g. 'SELECT * FROM CONVERSATIONS')")
                ),
                "required", List.of("sql")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String sql = String.valueOf(arguments.get("sql"));
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        if (lowerSql.contains("password") || lowerSql.contains("hash") || lowerSql.contains("jwt") || lowerSql.contains("secret") || lowerSql.contains("users") || lowerSql.contains("token_quota") || lowerSql.contains("drop") || lowerSql.contains("truncate") || lowerSql.contains("delete")) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Security Exception: Direct SQL queries targeting sensitive user credentials or data modification are restricted.")
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            String resultText = "Query returned " + rows.size() + " row(s):\n" + rows.toString();

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(resultText)
                    .success(true)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("SQL error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
