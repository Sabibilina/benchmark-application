package com.benchmark.analytics.messaging;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaybackEventConsumerTest {

    private static final String CANONICAL_USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void persistsValidPlaybackEvent() {
        AnalyticsEventRepository repository = Mockito.mock(AnalyticsEventRepository.class);
        PlaybackEventConsumer consumer = new PlaybackEventConsumer(repository);
        UUID eventId = UUID.randomUUID();

        consumer.consume(new PlaybackEvent(eventId, "play.started", CANONICAL_USER_ID, "song-1", Instant.parse("2026-05-14T10:00:00Z")));

        verify(repository).save(argThat(record ->
                record.eventId().equals(eventId)
                        && record.eventType().equals("play.started")
                        && record.userId().equals(CANONICAL_USER_ID)
                        && record.songId().equals("song-1")));
    }

    @Test
    void ignoresMalformedPlaybackEvent() {
        AnalyticsEventRepository repository = Mockito.mock(AnalyticsEventRepository.class);
        PlaybackEventConsumer consumer = new PlaybackEventConsumer(repository);

        consumer.consume(new PlaybackEvent(UUID.randomUUID(), "unknown", CANONICAL_USER_ID, "song-1", Instant.now()));
        consumer.consume(new PlaybackEvent(UUID.randomUUID(), "play.started", "analytics-smoke-user", "song-1", Instant.now()));
        consumer.consume(null);

        verify(repository, never()).save(Mockito.any());
    }
}
