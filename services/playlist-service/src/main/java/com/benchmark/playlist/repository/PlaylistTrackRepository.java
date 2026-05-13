package com.benchmark.playlist.repository;

import com.benchmark.playlist.entity.PlaylistTrack;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, UUID> {

    Optional<PlaylistTrack> findByPlaylistIdAndSongId(UUID playlistId, String songId);
}
