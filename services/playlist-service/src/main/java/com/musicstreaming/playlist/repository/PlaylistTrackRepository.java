package com.musicstreaming.playlist.repository;

import com.musicstreaming.playlist.entity.PlaylistTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, UUID> {

    boolean existsByPlaylistIdAndSongId(UUID playlistId, String songId);

    Optional<PlaylistTrack> findByPlaylistIdAndSongId(UUID playlistId, String songId);

    @Query("SELECT COALESCE(MAX(t.position), -1) FROM PlaylistTrack t WHERE t.playlist.id = :playlistId")
    int findMaxPositionByPlaylistId(@Param("playlistId") UUID playlistId);

    @Modifying
    @Query("UPDATE PlaylistTrack t SET t.position = :position WHERE t.id = :id")
    void updatePosition(@Param("id") UUID id, @Param("position") int position);
}
