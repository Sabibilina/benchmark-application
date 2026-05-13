package com.benchmark.playlist.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playlists")
public class Playlist {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, length = 255)
    private String ownerUserId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "liked_songs", nullable = false)
    private boolean likedSongs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<PlaylistTrack> tracks = new ArrayList<>();

    protected Playlist() {
    }

    public Playlist(String ownerUserId, String name, String description, boolean likedSongs, Instant now) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.description = description;
        this.likedSongs = likedSongs;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isLikedSongs() {
        return likedSongs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PlaylistTrack> getTracks() {
        return tracks;
    }

    public void rename(String name, String description, Instant now) {
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void addTrack(PlaylistTrack track, Instant now) {
        tracks.add(track);
        touch(now);
    }

    public void removeTrack(PlaylistTrack track, Instant now) {
        tracks.remove(track);
        track.detach();
        touch(now);
    }
}
