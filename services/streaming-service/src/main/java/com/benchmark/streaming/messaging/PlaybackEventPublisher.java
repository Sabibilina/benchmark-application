package com.benchmark.streaming.messaging;

import com.benchmark.streaming.config.StreamingProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaybackEventPublisher {

    private final KafkaTemplate<String, PlaybackEvent> kafkaTemplate;
    private final StreamingProperties properties;

    public PlaybackEventPublisher(KafkaTemplate<String, PlaybackEvent> kafkaTemplate, StreamingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(PlaybackEvent event) {
        kafkaTemplate.send(properties.playbackEventsTopic(), event.songId(), event);
    }
}
