package com.benchmark.streaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.streaming")
public record StreamingProperties(
        String playbackEventsTopic,
        int segmentCount,
        int segmentSizeBytes
) {

    public StreamingProperties {
        if (playbackEventsTopic == null || playbackEventsTopic.isBlank()) {
            playbackEventsTopic = "playback-events";
        }
        if (segmentCount < 1) {
            segmentCount = 5;
        }
        if (segmentSizeBytes < 1) {
            segmentSizeBytes = 65536;
        }
    }
}
