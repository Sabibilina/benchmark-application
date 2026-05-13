package com.benchmark.playlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistResponse(
        UUID id,
        String ownerUserId,
        String name,
        String description,
        boolean likedSongs,
        Instant createdAt,
        Instant updatedAt,
        List<PlaylistTrackResponse> tracks
) {
}
