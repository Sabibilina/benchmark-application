package com.musicstreaming.playlist.exception;

public class TrackNotFoundException extends RuntimeException {
    public TrackNotFoundException(String songId) {
        super("Song " + songId + " is not in the playlist");
    }
}
