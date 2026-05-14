package com.benchmark.notification.messaging;

import static org.mockito.Mockito.verify;

import com.benchmark.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaylistUpdateEventConsumerTest {

    NotificationService notificationService = Mockito.mock(NotificationService.class);
    PlaylistUpdateEventConsumer consumer = new PlaylistUpdateEventConsumer(notificationService);

    @Test
    void delegatesEventsToNotificationService() {
        PlaylistUpdateEvent event = event("playlist.updated");

        consumer.consume(event);

        verify(notificationService).processPlaylistUpdate(event);
    }

    @Test
    void swallowsValidationFailuresSoTheListenerCanContinue() {
        PlaylistUpdateEvent event = event("playlist.deleted");
        Mockito.doThrow(new IllegalArgumentException("unsupported eventType: playlist.deleted"))
                .when(notificationService)
                .processPlaylistUpdate(event);

        consumer.consume(event);

        verify(notificationService).processPlaylistUpdate(event);
    }

    private PlaylistUpdateEvent event(String eventType) {
        return new PlaylistUpdateEvent(
                UUID.randomUUID().toString(),
                eventType,
                "actor-user",
                List.of("recipient-user"),
                "playlist-1",
                "Road Trip",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("change", "track-added"));
    }
}
