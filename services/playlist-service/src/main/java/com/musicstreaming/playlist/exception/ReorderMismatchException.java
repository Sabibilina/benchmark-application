package com.musicstreaming.playlist.exception;

public class ReorderMismatchException extends RuntimeException {
    public ReorderMismatchException() {
        super("The provided song IDs do not match the current tracks in the playlist");
    }
}
