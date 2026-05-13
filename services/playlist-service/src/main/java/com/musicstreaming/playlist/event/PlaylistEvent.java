package com.musicstreaming.playlist.event;

import java.time.Instant;
import java.util.UUID;

public record PlaylistEvent(
        String eventType,
        UUID playlistId,
        String userId,
        String playlistName,
        Instant timestamp
) {
    public static PlaylistEvent of(String eventType, UUID playlistId, String userId, String playlistName) {
        return new PlaylistEvent(eventType, playlistId, userId, playlistName, Instant.now());
    }
}
