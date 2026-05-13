package com.benchmark.playlist.service;

public class PlaylistNotFoundException extends RuntimeException {

    public PlaylistNotFoundException() {
        super("Playlist was not found");
    }
}
