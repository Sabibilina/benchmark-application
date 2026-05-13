package com.benchmark.playlist.repository;

import com.benchmark.playlist.entity.Playlist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    List<Playlist> findByOwnerUserIdOrderByLikedSongsDescCreatedAtAsc(String ownerUserId);

    Optional<Playlist> findByOwnerUserIdAndLikedSongsTrue(String ownerUserId);

    Optional<Playlist> findByIdAndOwnerUserId(UUID id, String ownerUserId);

    @EntityGraph(attributePaths = "tracks")
    Optional<Playlist> findWithTracksByIdAndOwnerUserId(UUID id, String ownerUserId);
}
