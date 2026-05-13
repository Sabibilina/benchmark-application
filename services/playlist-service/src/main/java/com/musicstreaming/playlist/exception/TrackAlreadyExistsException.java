package com.musicstreaming.playlist.exception;

public class TrackAlreadyExistsException extends RuntimeException {
    public TrackAlreadyExistsException(String songId) {
        super("Song " + songId + " is already in the playlist");
    }
}
