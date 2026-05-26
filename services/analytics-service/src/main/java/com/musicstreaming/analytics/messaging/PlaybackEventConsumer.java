package com.musicstreaming.analytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.analytics.model.PlaybackEventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PlaybackEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlaybackEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final BatchEventBuffer batchEventBuffer;

    public PlaybackEventConsumer(ObjectMapper objectMapper, BatchEventBuffer batchEventBuffer) {
        this.objectMapper = objectMapper;
        this.batchEventBuffer = batchEventBuffer;
    }

    @KafkaListener(
            topics = "${kafka.topic.playback-events}",
            groupId = "${kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            PlaybackEventRecord event = objectMapper.readValue(message, PlaybackEventRecord.class);
            batchEventBuffer.add(event);
        } catch (Exception e) {
            log.warn("Failed to process playback event, skipping: {}", e.getMessage());
        }
    }
}
