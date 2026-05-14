package com.benchmark.analytics.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(AnalyticsEventRecord record) {
        jdbcTemplate.update("""
                        INSERT INTO playback_events (event_id, event_type, user_id, song_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                record.eventId(),
                record.eventType(),
                record.userId(),
                record.songId(),
                Timestamp.from(record.occurredAt()));
    }

    public List<AnalyticsEventRecord> findHistory(String userId, int page, int size) {
        return jdbcTemplate.query("""
                        SELECT event_id, event_type, user_id, song_id, occurred_at
                        FROM playback_events
                        WHERE user_id = ?
                        ORDER BY occurred_at DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new AnalyticsEventRecord(
                        UUID.fromString(rs.getString("event_id")),
                        rs.getString("event_type"),
                        rs.getString("user_id"),
                        rs.getString("song_id"),
                        toInstant(rs.getTimestamp("occurred_at"))),
                userId,
                size,
                page * size);
    }

    public long countHistory(String userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count() FROM playback_events WHERE user_id = ?",
                Long.class,
                userId);
        return count == null ? 0 : count;
    }

    public List<GlobalChartRecord> findGlobalChart(int limit) {
        return jdbcTemplate.query("""
                        SELECT song_id, count() AS play_count
                        FROM playback_events
                        WHERE event_type = 'play.started'
                        GROUP BY song_id
                        ORDER BY play_count DESC, song_id ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new GlobalChartRecord(rs.getString("song_id"), rs.getLong("play_count")),
                limit);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
