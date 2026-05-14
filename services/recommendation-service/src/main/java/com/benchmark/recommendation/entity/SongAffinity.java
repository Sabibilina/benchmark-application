package com.benchmark.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "song_affinities",
        uniqueConstraints = @UniqueConstraint(name = "uq_song_affinity_pair", columnNames = {
                "source_song_id", "related_song_id"
        }))
public class SongAffinity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_song_id", nullable = false)
    private String sourceSongId;

    @Column(name = "related_song_id", nullable = false)
    private String relatedSongId;

    @Column(nullable = false)
    private long score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SongAffinity() {
    }

    public SongAffinity(String sourceSongId, String relatedSongId, long score, Instant updatedAt) {
        this.sourceSongId = sourceSongId;
        this.relatedSongId = relatedSongId;
        this.score = score;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceSongId() {
        return sourceSongId;
    }

    public String getRelatedSongId() {
        return relatedSongId;
    }

    public long getScore() {
        return score;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
