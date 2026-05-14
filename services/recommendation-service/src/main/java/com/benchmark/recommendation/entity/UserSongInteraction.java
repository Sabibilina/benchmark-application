package com.benchmark.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_song_interactions")
public class UserSongInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "song_id", nullable = false)
    private String songId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "positive_signal", nullable = false)
    private boolean positiveSignal;

    protected UserSongInteraction() {
    }

    public UserSongInteraction(
            UUID eventId,
            String eventType,
            String userId,
            String songId,
            Instant occurredAt,
            boolean positiveSignal) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.userId = userId;
        this.songId = songId;
        this.occurredAt = occurredAt;
        this.positiveSignal = positiveSignal;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUserId() {
        return userId;
    }

    public String getSongId() {
        return songId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public boolean isPositiveSignal() {
        return positiveSignal;
    }
}
