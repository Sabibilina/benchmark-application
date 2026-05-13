package com.musicstreaming.playlist.exception;

import java.util.UUID;

public class PlaylistNotFoundException extends RuntimeException {
    public PlaylistNotFoundException(UUID id) {
        super("Playlist not found: " + id);
    }
}
