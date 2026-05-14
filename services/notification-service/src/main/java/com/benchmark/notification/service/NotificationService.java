package com.benchmark.notification.service;

import com.benchmark.notification.document.NotificationDocument;
import com.benchmark.notification.messaging.PlaylistUpdateEvent;
import com.benchmark.notification.repository.NotificationRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventMapper eventMapper;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository) {
        this(notificationRepository, new NotificationEventMapper());
    }

    NotificationService(NotificationRepository notificationRepository, NotificationEventMapper eventMapper) {
        this.notificationRepository = notificationRepository;
        this.eventMapper = eventMapper;
    }

    public List<NotificationDocument> processPlaylistUpdate(PlaylistUpdateEvent event) {
        List<NotificationDocument> notifications = eventMapper.toNotifications(event);
        String sourceEventId = notifications.getFirst().getSourceEventId();
        if (notificationRepository.existsBySourceEventId(sourceEventId)) {
            return List.of();
        }
        try {
            return notificationRepository.saveAll(notifications);
        } catch (DuplicateKeyException exception) {
            return List.of();
        }
    }

    public List<NotificationDocument> findForRecipient(String recipientUserId) {
        if (recipientUserId == null || recipientUserId.isBlank()) {
            throw new IllegalArgumentException("recipientUserId is required");
        }
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId.trim());
    }
}
