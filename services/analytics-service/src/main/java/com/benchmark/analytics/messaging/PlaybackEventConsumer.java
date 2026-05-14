package com.benchmark.analytics.messaging;

import com.benchmark.analytics.persistence.AnalyticsEventRecord;
import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PlaybackEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackEventConsumer.class);
    private static final List<String> SUPPORTED_EVENT_TYPES = List.of("play.started", "play.ended", "play.skipped");

    private final AnalyticsEventRepository repository;

    public PlaybackEventConsumer(AnalyticsEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.analytics.playback-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(PlaybackEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring malformed playback event: {}", event);
            return;
        }
        repository.save(AnalyticsEventRecord.from(event));
    }

    private boolean isValid(PlaybackEvent event) {
        return event != null
                && event.eventId() != null
                && SUPPORTED_EVENT_TYPES.contains(event.type())
                && event.userId() != null
                && !event.userId().isBlank()
                && event.songId() != null
                && !event.songId().isBlank()
                && event.timestamp() != null;
    }
}
