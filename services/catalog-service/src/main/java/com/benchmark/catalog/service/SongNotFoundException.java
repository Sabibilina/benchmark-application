package com.benchmark.catalog.service;

public class SongNotFoundException extends RuntimeException {

    public SongNotFoundException(String id) {
        super("Song not found: " + id);
    }
}
