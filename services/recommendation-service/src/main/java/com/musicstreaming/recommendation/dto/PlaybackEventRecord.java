package com.musicstreaming.recommendation.dto;

import java.time.Instant;

public record PlaybackEventRecord(
        String type,
        String userId,
        String songId,
        Instant timestamp
) {}
