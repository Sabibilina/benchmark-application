package com.benchmark.streaming.service;

import com.benchmark.streaming.config.StreamingProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class DummySegmentService {

    private final StreamingProperties properties;

    public DummySegmentService(StreamingProperties properties) {
        this.properties = properties;
    }

    public byte[] generate(String songId, int segmentIndex) {
        validateSongId(songId);
        if (segmentIndex < 0 || segmentIndex >= properties.segmentCount()) {
            throw new StreamingOperationException("Segment index is outside the configured stream range");
        }
        byte[] payload = new byte[properties.segmentSizeBytes()];
        byte[] seed = (songId + ":" + segmentIndex).getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (seed[i % seed.length] + i);
        }
        return payload;
    }

    static void validateSongId(String songId) {
        if (songId == null || songId.isBlank()) {
            throw new StreamingOperationException("Song id is required");
        }
    }
}
