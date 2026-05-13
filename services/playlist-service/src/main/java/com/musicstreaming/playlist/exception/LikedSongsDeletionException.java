package com.musicstreaming.playlist.exception;

public class LikedSongsDeletionException extends RuntimeException {
    public LikedSongsDeletionException() {
        super("The Liked Songs playlist cannot be deleted");
    }
}
