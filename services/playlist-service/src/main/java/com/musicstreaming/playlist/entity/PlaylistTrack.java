package com.musicstreaming.playlist.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "playlist_tracks")
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @Column(name = "song_id", nullable = false)
    private String songId;

    @Column(nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    protected PlaylistTrack() {}

    public PlaylistTrack(Playlist playlist, String songId, int position) {
        this.playlist = playlist;
        this.songId = songId;
        this.position = position;
    }

    public UUID getId() { return id; }
    public Playlist getPlaylist() { return playlist; }
    public String getSongId() { return songId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public OffsetDateTime getAddedAt() { return addedAt; }
}
