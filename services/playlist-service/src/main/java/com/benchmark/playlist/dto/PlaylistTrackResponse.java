package com.benchmark.playlist.dto;

import java.time.Instant;
import java.util.UUID;

public record PlaylistTrackResponse(
        UUID id,
        String songId,
        int position,
        Instant addedAt
) {
}
