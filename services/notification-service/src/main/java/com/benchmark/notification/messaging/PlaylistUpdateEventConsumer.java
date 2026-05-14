package com.benchmark.notification.messaging;

import com.benchmark.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PlaylistUpdateEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaylistUpdateEventConsumer.class);

    private final NotificationService notificationService;

    public PlaylistUpdateEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${app.notification.playlist-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    void consume(PlaylistUpdateEvent event) {
        try {
            notificationService.processPlaylistUpdate(event);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Skipping unsupported playlist notification event: {}", exception.getMessage());
        }
    }
}
