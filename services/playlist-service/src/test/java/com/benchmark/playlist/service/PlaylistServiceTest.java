package com.benchmark.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.benchmark.playlist.dto.AddTrackRequest;
import com.benchmark.playlist.dto.CreatePlaylistRequest;
import com.benchmark.playlist.dto.ReorderTracksRequest;
import com.benchmark.playlist.dto.UpdatePlaylistRequest;
import com.benchmark.playlist.entity.Playlist;
import com.benchmark.playlist.entity.PlaylistTrack;
import com.benchmark.playlist.repository.PlaylistRepository;
import com.benchmark.playlist.repository.PlaylistTrackRepository;
import com.benchmark.playlist.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlaylistServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private PlaylistRepository playlistRepository;
    private PlaylistTrackRepository playlistTrackRepository;
    private LikedSongsService likedSongsService;
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        playlistRepository = Mockito.mock(PlaylistRepository.class);
        playlistTrackRepository = Mockito.mock(PlaylistTrackRepository.class);
        likedSongsService = Mockito.mock(LikedSongsService.class);
        playlistService = new PlaylistService(playlistRepository, playlistTrackRepository, likedSongsService, clock);
    }

    @Test
    void createPlaylistStoresAuthenticatedOwner() {
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = playlistService.createPlaylist(
                new AuthenticatedUser("user-1"),
                new CreatePlaylistRequest(" Road Trip ", " Songs "));

        assertThat(response.ownerUserId()).isEqualTo("user-1");
        assertThat(response.name()).isEqualTo("Road Trip");
        assertThat(response.description()).isEqualTo("Songs");
        assertThat(response.likedSongs()).isFalse();
    }

    @Test
    void deleteRejectsLikedSongsPlaylist() {
        UUID playlistId = UUID.randomUUID();
        Playlist likedSongs = new Playlist("user-1", "Liked Songs", null, true, Instant.now(clock));
        when(playlistRepository.findWithTracksByIdAndOwnerUserId(playlistId, "user-1"))
                .thenReturn(Optional.of(likedSongs));

        assertThatThrownBy(() -> playlistService.deletePlaylist(new AuthenticatedUser("user-1"), playlistId))
                .isInstanceOf(PlaylistOperationException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void addTrackAppendsAfterExistingTracks() {
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = new Playlist("user-1", "Mix", null, false, Instant.now(clock));
        playlist.addTrack(new PlaylistTrack(playlist, "song-1", 0, Instant.now(clock)), Instant.now(clock));
        when(playlistRepository.findWithTracksByIdAndOwnerUserId(playlistId, "user-1"))
                .thenReturn(Optional.of(playlist));
        when(playlistTrackRepository.save(any(PlaylistTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = playlistService.addTrack(
                new AuthenticatedUser("user-1"),
                playlistId,
                new AddTrackRequest("song-2"));

        assertThat(result.created()).isTrue();
        assertThat(result.track().songId()).isEqualTo("song-2");
        assertThat(result.track().position()).isEqualTo(1);
    }

    @Test
    void reorderRequiresExactlyCurrentSongIds() {
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = new Playlist("user-1", "Mix", null, false, Instant.now(clock));
        playlist.addTrack(new PlaylistTrack(playlist, "song-1", 0, Instant.now(clock)), Instant.now(clock));
        playlist.addTrack(new PlaylistTrack(playlist, "song-2", 1, Instant.now(clock)), Instant.now(clock));
        when(playlistRepository.findWithTracksByIdAndOwnerUserId(playlistId, "user-1"))
                .thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> playlistService.reorderTracks(
                new AuthenticatedUser("user-1"),
                playlistId,
                new ReorderTracksRequest(List.of("song-1"))))
                .isInstanceOf(PlaylistOperationException.class);

        var reordered = playlistService.reorderTracks(
                new AuthenticatedUser("user-1"),
                playlistId,
                new ReorderTracksRequest(List.of("song-2", "song-1")));

        assertThat(reordered.tracks()).extracting("songId").containsExactly("song-2", "song-1");
    }

    @Test
    void reorderRejectsBlankAndDuplicateSongIds() {
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = new Playlist("user-1", "Mix", null, false, Instant.now(clock));
        playlist.addTrack(new PlaylistTrack(playlist, "song-1", 0, Instant.now(clock)), Instant.now(clock));
        playlist.addTrack(new PlaylistTrack(playlist, "song-2", 1, Instant.now(clock)), Instant.now(clock));
        when(playlistRepository.findWithTracksByIdAndOwnerUserId(playlistId, "user-1"))
                .thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> playlistService.reorderTracks(
                new AuthenticatedUser("user-1"),
                playlistId,
                new ReorderTracksRequest(List.of("song-1", " "))))
                .isInstanceOf(PlaylistOperationException.class)
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> playlistService.reorderTracks(
                new AuthenticatedUser("user-1"),
                playlistId,
                new ReorderTracksRequest(List.of("song-1", "song-1"))))
                .isInstanceOf(PlaylistOperationException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    void addTrackReturnsExistingTrackWithoutCreatingDuplicate() {
        UUID playlistId = UUID.randomUUID();
        Playlist playlist = new Playlist("user-1", "Mix", null, false, Instant.now(clock));
        playlist.addTrack(new PlaylistTrack(playlist, "song-1", 0, Instant.now(clock)), Instant.now(clock));
        when(playlistRepository.findWithTracksByIdAndOwnerUserId(playlistId, "user-1"))
                .thenReturn(Optional.of(playlist));

        var result = playlistService.addTrack(
                new AuthenticatedUser("user-1"),
                playlistId,
                new AddTrackRequest(" song-1 "));

        assertThat(result.created()).isFalse();
        assertThat(result.track().songId()).isEqualTo("song-1");
        Mockito.verifyNoInteractions(playlistTrackRepository);
    }

    @Test
    void updateRejectsCrossUserMissingPlaylist() {
        UUID playlistId = UUID.randomUUID();
        when(playlistRepository.findWithTracksByIdAndOwnerUserId(playlistId, "other-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playlistService.updatePlaylist(
                new AuthenticatedUser("other-user"),
                playlistId,
                new UpdatePlaylistRequest("New", null)))
                .isInstanceOf(PlaylistNotFoundException.class);
    }
}
