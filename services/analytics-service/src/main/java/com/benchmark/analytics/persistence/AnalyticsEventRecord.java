package com.benchmark.analytics.persistence;

import com.benchmark.analytics.messaging.PlaybackEvent;
import java.time.Instant;
import java.util.UUID;

public record AnalyticsEventRecord(
        UUID eventId,
        String eventType,
        String userId,
        String songId,
        Instant occurredAt
) {
    public static AnalyticsEventRecord from(PlaybackEvent event) {
        return new AnalyticsEventRecord(event.eventId(), event.type(), event.userId(), event.songId(), event.timestamp());
    }
}
