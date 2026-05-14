package com.benchmark.recommendation.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaybackEvent(
        String eventId,
        @JsonAlias("eventType") String type,
        String userId,
        String songId,
        @JsonAlias("occurredAt") Instant timestamp) {
}
