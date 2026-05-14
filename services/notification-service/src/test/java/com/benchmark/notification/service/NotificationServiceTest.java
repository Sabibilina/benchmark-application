package com.benchmark.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benchmark.notification.document.NotificationDocument;
import com.benchmark.notification.messaging.PlaylistUpdateEvent;
import com.benchmark.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository);
    }

    @Test
    void persistsNotificationsForAllRecipients() {
        PlaylistUpdateEvent event = event(List.of("user-a", "user-b"));
        when(repository.existsBySourceEventId(event.eventId())).thenReturn(false);
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<NotificationDocument> notifications = service.processPlaylistUpdate(event);

        assertThat(notifications).hasSize(2);
        verify(repository).saveAll(anyList());
    }

    @Test
    void ignoresDuplicateSourceEvents() {
        PlaylistUpdateEvent event = event(List.of("user-a"));
        when(repository.existsBySourceEventId(event.eventId())).thenReturn(true);

        List<NotificationDocument> notifications = service.processPlaylistUpdate(event);

        assertThat(notifications).isEmpty();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void retrievesNotificationsForRecipient() {
        NotificationDocument notification = new NotificationDocument(
                "user-a",
                "playlist.update",
                "Playlist updated",
                "Playlist \"Road Trip\" was updated.",
                UUID.randomUUID().toString(),
                "playlist.updated",
                "playlist-1",
                Map.of(),
                false,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findByRecipientUserIdOrderByCreatedAtDesc("user-a")).thenReturn(List.of(notification));

        assertThat(service.findForRecipient(" user-a ")).containsExactly(notification);
    }

    @Test
    void rejectsBlankRecipientLookup() {
        assertThatThrownBy(() -> service.findForRecipient(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recipientUserId is required");
    }

    private PlaylistUpdateEvent event(List<String> recipients) {
        return new PlaylistUpdateEvent(
                UUID.randomUUID().toString(),
                "playlist.updated",
                "actor-user",
                recipients,
                "playlist-1",
                "Road Trip",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("change", "track-added"));
    }
}
