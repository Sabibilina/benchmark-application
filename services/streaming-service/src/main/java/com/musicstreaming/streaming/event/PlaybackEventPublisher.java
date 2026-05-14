package com.musicstreaming.streaming.event;

import com.musicstreaming.streaming.dto.PlaybackEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaybackEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlaybackEventPublisher.class);

    private final KafkaTemplate<String, PlaybackEvent> kafkaTemplate;
    private final String topic;

    public PlaybackEventPublisher(KafkaTemplate<String, PlaybackEvent> kafkaTemplate,
                                  @Value("${kafka.topic.playback-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(PlaybackEvent event) {
        kafkaTemplate.send(topic, event.songId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish {} event for song {}: {}",
                                event.type(), event.songId(), ex.getMessage());
                    }
                });
    }
}
