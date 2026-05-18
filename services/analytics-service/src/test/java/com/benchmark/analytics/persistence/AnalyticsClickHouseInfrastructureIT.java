package com.benchmark.analytics.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AnalyticsClickHouseInfrastructureIT {

    @Test
    void savesAndReadsPlaybackEventsAgainstRealClickHouse() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        new AnalyticsSchemaInitializer(jdbcTemplate).run(null);
        AnalyticsEventRepository repository = new AnalyticsEventRepository(jdbcTemplate);
        String userId = "infra-user-" + UUID.randomUUID();
        String songId = "infra-song-" + UUID.randomUUID();
        AnalyticsEventRecord started = new AnalyticsEventRecord(
                UUID.randomUUID(),
                "play.started",
                userId,
                songId,
                Instant.parse("2026-01-01T00:00:00Z"));
        AnalyticsEventRecord ended = new AnalyticsEventRecord(
                UUID.randomUUID(),
                "play.ended",
                userId,
                songId,
                Instant.parse("2026-01-01T00:01:00Z"));

        repository.save(started);
        repository.save(ended);

        assertThat(repository.countHistory(userId)).isEqualTo(2);
        assertThat(repository.findHistory(userId, 0, 10))
                .extracting(AnalyticsEventRecord::eventType)
                .containsExactly("play.ended", "play.started");
        assertThat(repository.findGlobalChart(100))
                .anySatisfy(chart -> {
                    assertThat(chart.songId()).isEqualTo(songId);
                    assertThat(chart.playCount()).isGreaterThanOrEqualTo(1);
                });
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        dataSource.setUrl(System.getProperty(
                "infra.clickhouse.url",
                System.getenv().getOrDefault("INFRA_CLICKHOUSE_URL", "jdbc:clickhouse://analytics-db:8123/analytics")));
        dataSource.setUsername(System.getProperty(
                "infra.clickhouse.user",
                System.getenv().getOrDefault("INFRA_CLICKHOUSE_USER", "benchmark")));
        dataSource.setPassword(System.getProperty(
                "infra.clickhouse.password",
                System.getenv().getOrDefault("INFRA_CLICKHOUSE_PASSWORD", "benchmark")));
        return dataSource;
    }
}
