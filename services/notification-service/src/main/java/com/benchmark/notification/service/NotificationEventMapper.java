package com.benchmark.notification.service;

import com.benchmark.notification.document.NotificationDocument;
import com.benchmark.notification.messaging.PlaylistUpdateEvent;
import java.util.List;

public class NotificationEventMapper {

    public List<NotificationDocument> toNotifications(PlaylistUpdateEvent event) {
        validate(event);
        return event.recipientUserIds().stream()
                .map(recipient -> new NotificationDocument(
                        recipient.trim(),
                        "playlist.update",
                        "Playlist updated",
                        "Playlist \"" + event.playlistName().trim() + "\" was updated.",
                        event.eventId().trim(),
                        event.eventType().trim(),
                        event.playlistId().trim(),
                        event.metadata(),
                        false,
                        event.occurredAt()))
                .toList();
    }

    private void validate(PlaylistUpdateEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("playlist update event is required");
        }
        required(event.eventId(), "eventId");
        String eventType = required(event.eventType(), "eventType");
        required(event.actorUserId(), "actorUserId");
        required(event.playlistId(), "playlistId");
        required(event.playlistName(), "playlistName");
        if (!"playlist.updated".equals(eventType)) {
            throw new IllegalArgumentException("unsupported eventType: " + eventType);
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
            throw new IllegalArgumentException("recipientUserIds is required");
        }
        List<String> validRecipients = event.recipientUserIds().stream()
                .filter(recipient -> recipient != null && !recipient.isBlank())
                .toList();
        if (validRecipients.size() != event.recipientUserIds().size()) {
            throw new IllegalArgumentException("recipientUserIds cannot contain blank values");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
