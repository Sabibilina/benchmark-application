package com.musicstreaming.playlist.dto;

import com.musicstreaming.playlist.entity.Playlist;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlaylistSummaryResponse(
        UUID id,
        String name,
        int trackCount,
        boolean likedSongs,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PlaylistSummaryResponse from(Playlist playlist) {
        return new PlaylistSummaryResponse(
                playlist.getId(),
                playlist.getName(),
                playlist.getTracks().size(),
                playlist.isLikedSongs(),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt()
        );
    }
}
