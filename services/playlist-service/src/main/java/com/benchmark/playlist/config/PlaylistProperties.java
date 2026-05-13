package com.benchmark.playlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.playlist")
public record PlaylistProperties(String likedSongsName) {

    public PlaylistProperties {
        if (likedSongsName == null || likedSongsName.isBlank()) {
            likedSongsName = "Liked Songs";
        }
    }
}
