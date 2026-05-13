package com.benchmark.playlist.dto;

import java.time.Instant;
import java.util.UUID;

public record PlaylistSummaryResponse(
        UUID id,
        String ownerUserId,
        String name,
        String description,
        boolean likedSongs,
        int trackCount,
        Instant createdAt,
        Instant updatedAt
) {
}
