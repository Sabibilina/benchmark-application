package com.benchmark.streaming.messaging;

import java.time.Instant;
import java.util.UUID;

public record PlaybackEvent(
        UUID eventId,
        String type,
        String userId,
        String songId,
        Instant timestamp
) {
}
