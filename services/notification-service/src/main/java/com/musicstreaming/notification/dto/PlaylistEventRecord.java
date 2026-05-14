package com.musicstreaming.notification.dto;

import java.time.Instant;

public record PlaylistEventRecord(
        String eventType,
        String playlistId,
        String userId,
        String playlistName,
        Instant timestamp
) {}
