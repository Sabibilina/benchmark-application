package com.benchmark.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record HistoryEventResponse(
        UUID eventId,
        String type,
        String userId,
        String songId,
        Instant timestamp
) {
}
