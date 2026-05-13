package com.benchmark.playlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "playlist_tracks")
public class PlaylistTrack {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @Column(name = "song_id", nullable = false, length = 255)
    private String songId;

    @Column(name = "track_position", nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected PlaylistTrack() {
    }

    public PlaylistTrack(Playlist playlist, String songId, int position, Instant addedAt) {
        this.playlist = playlist;
        this.songId = songId;
        this.position = position;
        this.addedAt = addedAt;
    }

    public UUID getId() {
        return id;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public String getSongId() {
        return songId;
    }

    public int getPosition() {
        return position;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    void detach() {
        this.playlist = null;
    }
}
