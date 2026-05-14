package com.benchmark.analytics.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(createTableSql());
    }

    public String createTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS playback_events (
                  event_id UUID,
                  event_type String,
                  user_id String,
                  song_id String,
                  occurred_at DateTime64(3, 'UTC')
                )
                ENGINE = ReplacingMergeTree()
                ORDER BY (event_id, occurred_at)
                """;
    }
}
