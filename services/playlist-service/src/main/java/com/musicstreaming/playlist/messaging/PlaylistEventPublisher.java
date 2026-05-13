package com.musicstreaming.playlist.messaging;

import com.musicstreaming.playlist.event.PlaylistEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PlaylistEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlaylistEventPublisher.class);

    private final KafkaTemplate<String, PlaylistEvent> kafkaTemplate;
    private final String topic;

    public PlaylistEventPublisher(KafkaTemplate<String, PlaylistEvent> kafkaTemplate,
                                   @Value("${playlist.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(PlaylistEvent event) {
        kafkaTemplate.send(topic, event.playlistId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish playlist event {}: {}", event.eventType(), ex.getMessage());
                    }
                });
    }
}
