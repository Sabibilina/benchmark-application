package com.musicstreaming.notification.dto;

import com.musicstreaming.notification.model.Notification;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String type,
        String title,
        String message,
        String referenceId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getReferenceId(), n.isRead(), n.getCreatedAt());
    }
}
