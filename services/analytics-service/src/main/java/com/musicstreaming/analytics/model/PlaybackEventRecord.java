package com.musicstreaming.analytics.model;

import java.time.Instant;

public record PlaybackEventRecord(
        String type,
        String userId,
        String songId,
        Instant timestamp
) {}
