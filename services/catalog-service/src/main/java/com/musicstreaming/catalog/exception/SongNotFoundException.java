package com.musicstreaming.catalog.exception;

public class SongNotFoundException extends RuntimeException {
    public SongNotFoundException(Long id) {
        super("Song not found: " + id);
    }
}
