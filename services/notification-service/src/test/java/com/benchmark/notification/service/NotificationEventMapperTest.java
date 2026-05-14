package com.benchmark.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benchmark.notification.document.NotificationDocument;
import com.benchmark.notification.messaging.PlaylistUpdateEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventMapperTest {

    NotificationEventMapper mapper = new NotificationEventMapper();

    @Test
    void mapsPlaylistUpdateEventToNotificationForEachRecipient() {
        PlaylistUpdateEvent event = event("playlist.updated", List.of("user-a", "user-b"));

        List<NotificationDocument> notifications = mapper.toNotifications(event);

        assertThat(notifications).hasSize(2);
        assertThat(notifications).extracting(NotificationDocument::getRecipientUserId)
                .containsExactly("user-a", "user-b");
        assertThat(notifications.getFirst().getType()).isEqualTo("playlist.update");
        assertThat(notifications.getFirst().getTitle()).isEqualTo("Playlist updated");
        assertThat(notifications.getFirst().getMessage()).isEqualTo("Playlist \"Road Trip\" was updated.");
        assertThat(notifications.getFirst().isRead()).isFalse();
        assertThat(notifications.getFirst().getMetadata()).containsEntry("change", "track-added");
    }

    @Test
    void rejectsUnsupportedEvents() {
        PlaylistUpdateEvent event = event("playlist.deleted", List.of("user-a"));

        assertThatThrownBy(() -> mapper.toNotifications(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported eventType: playlist.deleted");
    }

    @Test
    void rejectsBlankRecipients() {
        PlaylistUpdateEvent event = event("playlist.updated", List.of("user-a", " "));

        assertThatThrownBy(() -> mapper.toNotifications(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recipientUserIds cannot contain blank values");
    }

    private PlaylistUpdateEvent event(String eventType, List<String> recipients) {
        return new PlaylistUpdateEvent(
                UUID.randomUUID().toString(),
                eventType,
                "actor-user",
                recipients,
                "playlist-1",
                "Road Trip",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("change", "track-added"));
    }
}
