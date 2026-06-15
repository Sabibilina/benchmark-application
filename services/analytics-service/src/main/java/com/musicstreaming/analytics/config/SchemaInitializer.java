package com.musicstreaming.analytics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS playback_events (
                    event_type   LowCardinality(String),
                    user_id      String,
                    song_id      String,
                    occurred_at  DateTime64(3, 'UTC')
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(toDateTime(occurred_at))
                ORDER BY (user_id, song_id, occurred_at)
                """);
        log.info("ClickHouse schema ready");
    }
}
