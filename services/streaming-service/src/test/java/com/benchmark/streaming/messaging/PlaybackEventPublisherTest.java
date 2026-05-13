package com.benchmark.streaming.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.benchmark.streaming.config.StreamingProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class PlaybackEventPublisherTest {

    @Test
    void publishSendsEventToConfiguredTopicUsingSongIdKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PlaybackEvent> kafkaTemplate = mock(KafkaTemplate.class);
        PlaybackEventPublisher publisher = new PlaybackEventPublisher(
                kafkaTemplate,
                new StreamingProperties("playback-events", 5, 1024));
        PlaybackEvent event = new PlaybackEvent(
                UUID.randomUUID(),
                "play.started",
                "user-1",
                "song-1",
                Instant.parse("2026-01-01T00:00:00Z"));

        publisher.publish(event);

        verify(kafkaTemplate).send("playback-events", "song-1", event);
    }
}
