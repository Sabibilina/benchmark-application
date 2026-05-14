package com.benchmark.recommendation.messaging;

import com.benchmark.recommendation.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PlaybackEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackEventConsumer.class);

    private final RecommendationService recommendationService;

    public PlaybackEventConsumer(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @KafkaListener(
            topics = "${app.recommendation.playback-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    void consume(PlaybackEvent event) {
        try {
            recommendationService.recordPlaybackEvent(event);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Skipping unsupported playback event: {}", exception.getMessage());
        }
    }
}
