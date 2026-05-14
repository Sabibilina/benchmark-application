package com.musicstreaming.analytics.dto;

import java.time.Instant;

public record HistoryEntry(
        String songId,
        String eventType,
        Instant timestamp
) {}
