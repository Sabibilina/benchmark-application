package com.musicstreaming.playlist.exception;

public class ReservedNameException extends RuntimeException {
    public ReservedNameException(String name) {
        super("\"" + name + "\" is a reserved playlist name");
    }
}
