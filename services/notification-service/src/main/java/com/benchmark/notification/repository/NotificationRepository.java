package com.benchmark.notification.repository;

import com.benchmark.notification.document.NotificationDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    boolean existsBySourceEventId(String sourceEventId);

    List<NotificationDocument> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
}
