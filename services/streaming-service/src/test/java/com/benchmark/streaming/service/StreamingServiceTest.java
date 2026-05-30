package com.benchmark.streaming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.benchmark.streaming.config.StreamingProperties;
import com.benchmark.streaming.messaging.PlaybackEvent;
import com.benchmark.streaming.messaging.PlaybackEventPublisher;
import com.benchmark.streaming.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StreamingServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private PlaybackEventPublisher publisher;
    private StreamingService service;

    @BeforeEach
    void setUp() {
        StreamingProperties properties = new StreamingProperties("playback-events", 2, 64);
        publisher = mock(PlaybackEventPublisher.class);
        service = new StreamingService(
                new StreamDescriptorService(properties, clock),
                new DummySegmentService(properties),
                publisher,
                clock);
    }

    @Test
    void startStreamReturnsDescriptorAndPublishesStartedEvent() {
        var descriptor = service.startStream(new AuthenticatedUser("user-1"), "song-1");

        assertThat(descriptor.songId()).isEqualTo("song-1");
        assertThat(descriptor.segmentCount()).isEqualTo(2);
        assertThat(descriptor.segments()).hasSize(2);

        ArgumentCaptor<PlaybackEvent> event = forClass(PlaybackEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().type()).isEqualTo("play.started");
        assertThat(event.getValue().userId()).isEqualTo("user-1");
        assertThat(event.getValue().songId()).isEqualTo("song-1");
        assertThat(event.getValue().timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(event.getValue().eventId()).isNotNull();
    }

    @Test
    void endedAndSkippedPublishRequiredEvents() {
        PlaybackEvent ended = service.ended(new AuthenticatedUser("user-1"), "song-1");
        PlaybackEvent skipped = service.skipped(new AuthenticatedUser("user-1"), "song-1");

        assertThat(ended.type()).isEqualTo("play.ended");
        assertThat(skipped.type()).isEqualTo("play.skipped");
    }

    @Test
    void streamingPropertiesApplySafeDefaultsForMissingOrInvalidValues() {
        StreamingProperties properties = new StreamingProperties("  ", 0, -1);

        assertThat(properties.playbackEventsTopic()).isEqualTo("playback-events");
        assertThat(properties.segmentCount()).isEqualTo(5);
        assertThat(properties.segmentSizeBytes()).isEqualTo(65536);
    }
}
