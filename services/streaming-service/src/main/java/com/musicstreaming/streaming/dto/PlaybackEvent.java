package com.musicstreaming.streaming.dto;

import java.time.Instant;

public record PlaybackEvent(
        String type,
        String userId,
        String songId,
        Instant timestamp
) {}
