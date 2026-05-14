package com.musicstreaming.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.notification.dto.PlaylistEventRecord;
import com.musicstreaming.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PlaylistEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlaylistEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public PlaylistEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${kafka.topic.playlist-events}",
            groupId = "${kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            PlaylistEventRecord event = objectMapper.readValue(message, PlaylistEventRecord.class);

            if (event.userId() == null || event.playlistId() == null) {
                log.debug("Dropping playlist event with null userId or playlistId");
                return;
            }

            notificationService.createNotification(event);
        } catch (Exception e) {
            log.warn("Failed to process playlist event, skipping: {}", e.getMessage());
        }
    }
}
