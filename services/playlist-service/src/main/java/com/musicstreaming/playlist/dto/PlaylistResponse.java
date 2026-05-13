package com.musicstreaming.playlist.dto;

import com.musicstreaming.playlist.entity.Playlist;
import com.musicstreaming.playlist.entity.PlaylistTrack;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PlaylistResponse(
        UUID id,
        String name,
        boolean likedSongs,
        List<TrackResponse> tracks,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PlaylistResponse from(Playlist playlist) {
        List<TrackResponse> tracks = playlist.getTracks().stream()
                .sorted(Comparator.comparingInt(t -> t.getPosition()))
                .map(TrackResponse::from)
                .toList();
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getName(),
                playlist.isLikedSongs(),
                tracks,
                playlist.getCreatedAt(),
                playlist.getUpdatedAt()
        );
    }
}
