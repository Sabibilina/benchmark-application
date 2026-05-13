package com.benchmark.playlist.security;

public record AuthenticatedUser(String userId) {

    public AuthenticatedUser {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user id is required");
        }
    }
}
