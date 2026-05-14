package com.musicstreaming.notification.unit;

import com.musicstreaming.notification.dto.PlaylistEventRecord;
import com.musicstreaming.notification.model.Notification;
import com.musicstreaming.notification.repository.NotificationRepository;
import com.musicstreaming.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService service;

    private PlaylistEventRecord event(String eventType) {
        return new PlaylistEventRecord(
                eventType, "playlist-uuid", "user1", "My Playlist", Instant.now());
    }

    @Test
    void createNotification_savesWithCorrectUserId() {
        service.createNotification(event("PLAYLIST_CREATED"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user1");
    }

    @Test
    void createNotification_savesWithCorrectType() {
        service.createNotification(event("TRACK_ADDED"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("TRACK_ADDED");
    }

    @Test
    void createNotification_setsReadFalse() {
        service.createNotification(event("PLAYLIST_CREATED"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    void createNotification_setsReferenceId() {
        service.createNotification(event("TRACK_ADDED"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReferenceId()).isEqualTo("playlist-uuid");
    }

    @Test
    void createNotification_nullTimestamp_usesNow() {
        PlaylistEventRecord eventWithNullTs = new PlaylistEventRecord(
                "TRACK_REMOVED", "ref", "user1", "Playlist", null);

        service.createNotification(eventWithNullTs);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "PLAYLIST_CREATED", "PLAYLIST_UPDATED", "PLAYLIST_DELETED",
        "TRACK_ADDED", "TRACK_REMOVED", "TRACKS_REORDERED"
    })
    void createNotification_allEventTypes_producesNonBlankTitleAndMessage(String eventType) {
        service.createNotification(event(eventType));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isNotBlank();
        assertThat(captor.getValue().getMessage()).isNotBlank();
    }

    @Test
    void getNotificationsForUser_delegatesToRepository() {
        String userId = "user1";
        Notification stub = new Notification();
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(stub));

        List<Notification> result = service.getNotificationsForUser(userId);

        assertThat(result).containsExactly(stub);
        verify(repository).findByUserIdOrderByCreatedAtDesc(userId);
    }
}
