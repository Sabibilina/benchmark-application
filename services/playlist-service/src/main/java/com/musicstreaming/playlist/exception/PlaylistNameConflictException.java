package com.musicstreaming.playlist.exception;

public class PlaylistNameConflictException extends RuntimeException {
    public PlaylistNameConflictException(String name) {
        super("A playlist named \"" + name + "\" already exists");
    }
}
