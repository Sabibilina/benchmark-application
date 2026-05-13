package com.benchmark.playlist.service;

public class PlaylistTrackNotFoundException extends RuntimeException {

    public PlaylistTrackNotFoundException() {
        super("Playlist track was not found");
    }
}
