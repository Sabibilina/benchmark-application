package com.benchmark.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaylistUpdateEvent(
        String eventId,
        String eventType,
        String actorUserId,
        List<String> recipientUserIds,
        String playlistId,
        String playlistName,
        Instant occurredAt,
        Map<String, Object> metadata) {
}
