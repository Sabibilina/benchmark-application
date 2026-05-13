package com.benchmark.streaming.service;

import com.benchmark.streaming.dto.StreamDescriptorResponse;
import com.benchmark.streaming.messaging.PlaybackEvent;
import com.benchmark.streaming.messaging.PlaybackEventPublisher;
import com.benchmark.streaming.messaging.PlaybackEventType;
import com.benchmark.streaming.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StreamingService {

    private final StreamDescriptorService descriptorService;
    private final DummySegmentService dummySegmentService;
    private final PlaybackEventPublisher eventPublisher;
    private final Clock clock;

    public StreamingService(
            StreamDescriptorService descriptorService,
            DummySegmentService dummySegmentService,
            PlaybackEventPublisher eventPublisher,
            Clock clock
    ) {
        this.descriptorService = descriptorService;
        this.dummySegmentService = dummySegmentService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public StreamDescriptorResponse startStream(AuthenticatedUser user, String songId) {
        StreamDescriptorResponse descriptor = descriptorService.descriptor(songId);
        publish(PlaybackEventType.STARTED, user, songId);
        return descriptor;
    }

    public byte[] segment(String songId, int segmentIndex) {
        return dummySegmentService.generate(songId, segmentIndex);
    }

    public PlaybackEvent ended(AuthenticatedUser user, String songId) {
        return publish(PlaybackEventType.ENDED, user, songId);
    }

    public PlaybackEvent skipped(AuthenticatedUser user, String songId) {
        return publish(PlaybackEventType.SKIPPED, user, songId);
    }

    private PlaybackEvent publish(PlaybackEventType type, AuthenticatedUser user, String songId) {
        DummySegmentService.validateSongId(songId);
        PlaybackEvent event = new PlaybackEvent(
                UUID.randomUUID(),
                type.value(),
                user.userId(),
                songId,
                Instant.now(clock));
        eventPublisher.publish(event);
        return event;
    }
}
