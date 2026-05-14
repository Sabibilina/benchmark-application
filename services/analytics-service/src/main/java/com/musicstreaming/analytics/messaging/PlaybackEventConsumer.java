package com.musicstreaming.analytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.analytics.model.PlaybackEventRecord;
import com.musicstreaming.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PlaybackEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlaybackEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    public PlaybackEventConsumer(ObjectMapper objectMapper, AnalyticsService analyticsService) {
        this.objectMapper = objectMapper;
        this.analyticsService = analyticsService;
    }

    @KafkaListener(
            topics = "${kafka.topic.playback-events}",
            groupId = "${kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            PlaybackEventRecord event = objectMapper.readValue(message, PlaybackEventRecord.class);
            analyticsService.recordEvent(event);
        } catch (Exception e) {
            log.warn("Failed to process playback event, skipping: {}", e.getMessage());
        }
    }
}
