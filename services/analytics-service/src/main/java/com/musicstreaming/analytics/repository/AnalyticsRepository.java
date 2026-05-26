package com.musicstreaming.analytics.repository;

import com.musicstreaming.analytics.dto.ChartEntry;
import com.musicstreaming.analytics.dto.HistoryEntry;
import com.musicstreaming.analytics.model.PlaybackEventRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(PlaybackEventRecord event) {
        Instant ts = event.timestamp() != null ? event.timestamp() : Instant.now();
        jdbcTemplate.update(
                "INSERT INTO playback_events (event_type, user_id, song_id, occurred_at) VALUES (?, ?, ?, ?)",
                event.type(), event.userId(), event.songId(), Timestamp.from(ts));
    }

    public void insertBatch(List<PlaybackEventRecord> events) {
        if (events.isEmpty()) return;
        String sql = "INSERT INTO playback_events (event_type, user_id, song_id, occurred_at) VALUES (?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, events, events.size(), (ps, event) -> {
            Instant ts = event.timestamp() != null ? event.timestamp() : Instant.now();
            ps.setString(1, event.type());
            ps.setString(2, event.userId());
            ps.setString(3, event.songId());
            ps.setTimestamp(4, Timestamp.from(ts));
        });
    }

    public List<HistoryEntry> findHistoryForUser(String userId, int limit) {
        return jdbcTemplate.query(
                "SELECT song_id, event_type, occurred_at " +
                "FROM playback_events " +
                "WHERE user_id = ? " +
                "ORDER BY occurred_at DESC " +
                "LIMIT ?",
                (rs, rowNum) -> new HistoryEntry(
                        rs.getString("song_id"),
                        rs.getString("event_type"),
                        rs.getTimestamp("occurred_at").toInstant()),
                userId, limit);
    }

    public List<ChartEntry> findGlobalCharts(int limit) {
        return jdbcTemplate.query(
                "SELECT song_id, count(*) AS play_count " +
                "FROM playback_events " +
                "WHERE event_type = 'play.started' " +
                "GROUP BY song_id " +
                "ORDER BY play_count DESC " +
                "LIMIT ?",
                (rs, rowNum) -> new ChartEntry(
                        rowNum + 1,
                        rs.getString("song_id"),
                        rs.getLong("play_count")),
                limit);
    }
}
