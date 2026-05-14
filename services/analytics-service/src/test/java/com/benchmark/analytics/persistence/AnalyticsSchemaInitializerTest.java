package com.benchmark.analytics.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsSchemaInitializerTest {

    @Test
    void createTableSqlIsIdempotentClickHouseSql() {
        AnalyticsSchemaInitializer initializer = new AnalyticsSchemaInitializer(Mockito.mock(JdbcTemplate.class));

        assertThat(initializer.createTableSql())
                .contains("CREATE TABLE IF NOT EXISTS playback_events")
                .contains("ReplacingMergeTree")
                .contains("event_id UUID");
    }

    @Test
    void runExecutesSchemaSql() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        AnalyticsSchemaInitializer initializer = new AnalyticsSchemaInitializer(jdbcTemplate);

        initializer.run(null);

        verify(jdbcTemplate).execute(initializer.createTableSql());
    }
}
