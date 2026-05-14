package com.musicstreaming.streaming.service;

import com.musicstreaming.streaming.dto.PlaybackEvent;
import com.musicstreaming.streaming.event.PlaybackEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class StreamingService {

    public static final String EVENT_STARTED = "play.started";
    public static final String EVENT_ENDED = "play.ended";
    public static final String EVENT_SKIPPED = "play.skipped";

    private final PlaybackEventPublisher publisher;
    private final int segmentSizeBytes;
    private final int segmentCount;
    private final SecureRandom random = new SecureRandom();

    public StreamingService(PlaybackEventPublisher publisher,
                            @Value("${stream.segment.size-bytes}") int segmentSizeBytes,
                            @Value("${stream.segment.count}") int segmentCount) {
        this.publisher = publisher;
        this.segmentSizeBytes = segmentSizeBytes;
        this.segmentCount = segmentCount;
    }

    public String buildManifest(String songId) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:3\n");
        sb.append("#EXT-X-TARGETDURATION:10\n");
        sb.append("#EXT-X-MEDIA-SEQUENCE:0\n");
        for (int i = 0; i < segmentCount; i++) {
            sb.append("#EXTINF:10.0,\n");
            sb.append("/stream/").append(songId).append("/segment/").append(i).append("\n");
        }
        sb.append("#EXT-X-ENDLIST\n");
        return sb.toString();
    }

    public byte[] generateSegmentPayload() {
        byte[] payload = new byte[segmentSizeBytes];
        random.nextBytes(payload);
        return payload;
    }

    public void handleStreamStart(String userId, String songId) {
        publisher.publish(new PlaybackEvent(EVENT_STARTED, userId, songId, Instant.now()));
    }

    public void handleStreamComplete(String userId, String songId) {
        publisher.publish(new PlaybackEvent(EVENT_ENDED, userId, songId, Instant.now()));
    }

    public void handleStreamSkip(String userId, String songId) {
        publisher.publish(new PlaybackEvent(EVENT_SKIPPED, userId, songId, Instant.now()));
    }
}
