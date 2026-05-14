package com.benchmark.recommendation.messaging;

import static org.mockito.Mockito.verify;

import com.benchmark.recommendation.service.RecommendationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaybackEventConsumerTest {

    RecommendationService recommendationService = Mockito.mock(RecommendationService.class);
    PlaybackEventConsumer consumer = new PlaybackEventConsumer(recommendationService);

    @Test
    void delegatesValidEventsToRecommendationService() {
        PlaybackEvent event = new PlaybackEvent(
                UUID.randomUUID().toString(),
                "play.started",
                UUID.randomUUID().toString(),
                "song-a",
                Instant.parse("2026-01-01T00:00:00Z"));

        consumer.consume(event);

        verify(recommendationService).recordPlaybackEvent(event);
    }

    @Test
    void swallowsUnsupportedEventsSoTheListenerCanContinue() {
        PlaybackEvent event = new PlaybackEvent(
                UUID.randomUUID().toString(),
                "play.paused",
                UUID.randomUUID().toString(),
                "song-a",
                Instant.parse("2026-01-01T00:00:00Z"));
        Mockito.doThrow(new IllegalArgumentException("unsupported type: play.paused"))
                .when(recommendationService)
                .recordPlaybackEvent(event);

        consumer.consume(event);

        verify(recommendationService).recordPlaybackEvent(event);
    }

    @Test
    void nullEventIsHandledByServiceValidation() {
        Mockito.doThrow(new IllegalArgumentException("playback event is required"))
                .when(recommendationService)
                .recordPlaybackEvent(null);

        consumer.consume(null);

        verify(recommendationService).recordPlaybackEvent(null);
    }
}
