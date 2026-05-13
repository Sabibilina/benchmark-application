package com.benchmark.playlist.service;

import com.benchmark.playlist.config.PlaylistProperties;
import com.benchmark.playlist.entity.Playlist;
import com.benchmark.playlist.repository.PlaylistRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikedSongsService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistProperties properties;
    private final Clock clock;

    public LikedSongsService(PlaylistRepository playlistRepository, PlaylistProperties properties, Clock clock) {
        this.playlistRepository = playlistRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Playlist ensureLikedSongs(String ownerUserId) {
        return playlistRepository.findByOwnerUserIdAndLikedSongsTrue(ownerUserId)
                .orElseGet(() -> playlistRepository.save(new Playlist(
                        ownerUserId,
                        properties.likedSongsName(),
                        null,
                        true,
                        Instant.now(clock))));
    }
}
