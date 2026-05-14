package com.musicstreaming.notification.service;

import com.musicstreaming.notification.dto.PlaylistEventRecord;
import com.musicstreaming.notification.model.Notification;
import com.musicstreaming.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void createNotification(PlaylistEventRecord event) {
        Notification notification = new Notification();
        notification.setUserId(event.userId());
        notification.setType(event.eventType());
        notification.setTitle(titleFor(event));
        notification.setMessage(messageFor(event));
        notification.setReferenceId(event.playlistId());
        notification.setRead(false);
        notification.setCreatedAt(event.timestamp() != null ? event.timestamp() : Instant.now());
        repository.save(notification);
    }

    public List<Notification> getNotificationsForUser(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    static String titleFor(PlaylistEventRecord event) {
        return switch (event.eventType()) {
            case "PLAYLIST_CREATED"  -> "New playlist created";
            case "PLAYLIST_UPDATED"  -> "Playlist updated";
            case "PLAYLIST_DELETED"  -> "Playlist deleted";
            case "TRACK_ADDED"       -> "Track added";
            case "TRACK_REMOVED"     -> "Track removed";
            case "TRACKS_REORDERED"  -> "Playlist reordered";
            default                  -> "Playlist activity";
        };
    }

    static String messageFor(PlaylistEventRecord event) {
        String name = event.playlistName() != null ? event.playlistName() : "Unknown";
        return switch (event.eventType()) {
            case "PLAYLIST_CREATED"  -> "\"" + name + "\" has been created.";
            case "PLAYLIST_UPDATED"  -> "\"" + name + "\" has been updated.";
            case "PLAYLIST_DELETED"  -> "\"" + name + "\" has been deleted.";
            case "TRACK_ADDED"       -> "A track was added to \"" + name + "\".";
            case "TRACK_REMOVED"     -> "A track was removed from \"" + name + "\".";
            case "TRACKS_REORDERED"  -> "Tracks in \"" + name + "\" were reordered.";
            default                  -> "Activity on \"" + name + "\".";
        };
    }
}
