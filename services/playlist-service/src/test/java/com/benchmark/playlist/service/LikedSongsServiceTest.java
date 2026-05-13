package com.benchmark.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.benchmark.playlist.config.PlaylistProperties;
import com.benchmark.playlist.entity.Playlist;
import com.benchmark.playlist.repository.PlaylistRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LikedSongsServiceTest {

    @Test
    void ensureLikedSongsCreatesSpecialPlaylistWhenMissing() {
        PlaylistRepository playlistRepository = Mockito.mock(PlaylistRepository.class);
        when(playlistRepository.findByOwnerUserIdAndLikedSongsTrue("user-1")).thenReturn(Optional.empty());
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LikedSongsService service = new LikedSongsService(
                playlistRepository,
                new PlaylistProperties("Liked Songs"),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        Playlist playlist = service.ensureLikedSongs("user-1");

        assertThat(playlist.getOwnerUserId()).isEqualTo("user-1");
        assertThat(playlist.getName()).isEqualTo("Liked Songs");
        assertThat(playlist.isLikedSongs()).isTrue();
    }

    @Test
    void ensureLikedSongsReusesExistingSpecialPlaylist() {
        PlaylistRepository playlistRepository = Mockito.mock(PlaylistRepository.class);
        Playlist existing = new Playlist(
                "user-1",
                "Liked Songs",
                null,
                true,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(playlistRepository.findByOwnerUserIdAndLikedSongsTrue("user-1")).thenReturn(Optional.of(existing));
        LikedSongsService service = new LikedSongsService(
                playlistRepository,
                new PlaylistProperties("Liked Songs"),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.ensureLikedSongs("user-1")).isSameAs(existing);
    }
}
