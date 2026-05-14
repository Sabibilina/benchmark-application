package com.musicstreaming.recommendation.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.recommendation.dto.PlaybackEventRecord;
import com.musicstreaming.recommendation.model.PlayEvent;
import com.musicstreaming.recommendation.repository.PlayEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PlaybackEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlaybackEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PlayEventRepository playEventRepository;

    public PlaybackEventConsumer(ObjectMapper objectMapper, PlayEventRepository playEventRepository) {
        this.objectMapper = objectMapper;
        this.playEventRepository = playEventRepository;
    }

    @KafkaListener(
            topics = "${kafka.topic.playback-events}",
            groupId = "${kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            PlaybackEventRecord event = objectMapper.readValue(message, PlaybackEventRecord.class);

            if (event.userId() == null || event.songId() == null) {
                log.debug("Dropping event with null userId or songId");
                return;
            }

            if (!"play.started".equals(event.type())) {
                return;
            }

            PlayEvent playEvent = new PlayEvent();
            playEvent.setUserId(event.userId());
            playEvent.setSongId(event.songId());
            playEvent.setEventType(event.type());
            LocalDateTime occurredAt = event.timestamp() != null
                    ? LocalDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC)
                    : LocalDateTime.now(ZoneOffset.UTC);
            playEvent.setOccurredAt(occurredAt);

            playEventRepository.save(playEvent);
        } catch (Exception e) {
            log.warn("Failed to process playback event, skipping: {}", e.getMessage());
        }
    }
}
