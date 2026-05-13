package com.benchmark.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "songs")
public class Song {

    @Id
    @Column(name = "id", nullable = false, length = 120)
    private String id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "artist", nullable = false, length = 500)
    private String artist;

    @Column(name = "album", length = 500)
    private String album;

    @Column(name = "genre", length = 200)
    private String genre;

    @Column(name = "bpm", precision = 8, scale = 3)
    private BigDecimal bpm;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "popularity")
    private Integer popularity;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Convert(converter = StringMapConverter.class)
    @Column(name = "metadata", nullable = false, columnDefinition = "TEXT")
    private Map<String, String> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Song() {
    }

    public Song(String id, String title, String artist) {
        this.id = id;
        this.title = title;
        this.artist = artist;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public BigDecimal getBpm() {
        return bpm;
    }

    public void setBpm(BigDecimal bpm) {
        this.bpm = bpm;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Integer getPopularity() {
        return popularity;
    }

    public void setPopularity(Integer popularity) {
        this.popularity = popularity;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = new LinkedHashMap<>(metadata);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
