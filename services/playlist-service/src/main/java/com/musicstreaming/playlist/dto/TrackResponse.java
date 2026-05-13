package com.musicstreaming.playlist.dto;

import com.musicstreaming.playlist.entity.PlaylistTrack;
import java.time.OffsetDateTime;

public record TrackResponse(
        String songId,
        int position,
        OffsetDateTime addedAt
) {
    public static TrackResponse from(PlaylistTrack track) {
        return new TrackResponse(track.getSongId(), track.getPosition(), track.getAddedAt());
    }
}
