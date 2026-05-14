package com.benchmark.analytics.persistence;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsEventRepositoryTest {

    private static final String CANONICAL_USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void saveInsertsPlaybackEventRecord() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        AnalyticsEventRepository repository = new AnalyticsEventRepository(jdbcTemplate);
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-05-14T10:00:00Z");

        repository.save(new AnalyticsEventRecord(eventId, "play.started", CANONICAL_USER_ID, "song-1", occurredAt));

        verify(jdbcTemplate).update(contains("INSERT INTO playback_events"),
                eq(eventId), eq("play.started"), eq(CANONICAL_USER_ID), eq("song-1"), eq(Timestamp.from(occurredAt)));
    }

    @Test
    void countHistoryReturnsZeroWhenJdbcReturnsNull() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(Mockito.anyString(), eq(Long.class), eq(CANONICAL_USER_ID))).thenReturn(null);
        AnalyticsEventRepository repository = new AnalyticsEventRepository(jdbcTemplate);

        org.assertj.core.api.Assertions.assertThat(repository.countHistory(CANONICAL_USER_ID)).isZero();
    }
}
