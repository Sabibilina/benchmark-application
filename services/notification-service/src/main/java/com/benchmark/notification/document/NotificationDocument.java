package com.benchmark.notification.document;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notifications")
@CompoundIndex(name = "recipient_created_at_idx", def = "{'recipientUserId': 1, 'createdAt': -1}")
@CompoundIndex(name = "source_event_recipient_idx", def = "{'sourceEventId': 1, 'recipientUserId': 1}", unique = true)
public class NotificationDocument {

    @Id
    private String id;

    @Indexed
    private String recipientUserId;

    private String type;

    private String title;

    private String message;

    @Indexed
    private String sourceEventId;

    private String sourceEventType;

    private String sourceAggregateId;

    private Map<String, Object> metadata;

    private boolean read;

    private Instant createdAt;

    protected NotificationDocument() {
    }

    public NotificationDocument(
            String recipientUserId,
            String type,
            String title,
            String message,
            String sourceEventId,
            String sourceEventType,
            String sourceAggregateId,
            Map<String, Object> metadata,
            boolean read,
            Instant createdAt) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.sourceEventId = sourceEventId;
        this.sourceEventType = sourceEventType;
        this.sourceAggregateId = sourceAggregateId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.read = read;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public String getSourceEventType() {
        return sourceEventType;
    }

    public String getSourceAggregateId() {
        return sourceAggregateId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
