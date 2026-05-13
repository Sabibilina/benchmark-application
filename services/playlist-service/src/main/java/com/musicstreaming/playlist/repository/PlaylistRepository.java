package com.musicstreaming.playlist.repository;

import com.musicstreaming.playlist.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    List<Playlist> findAllByUserId(String userId);

    Optional<Playlist> findByUserIdAndLikedSongsTrue(String userId);

    boolean existsByUserIdAndName(String userId, String name);
}
