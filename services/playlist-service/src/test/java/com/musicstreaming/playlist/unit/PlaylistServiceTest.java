package com.musicstreaming.playlist.unit;

import com.musicstreaming.playlist.dto.*;
import com.musicstreaming.playlist.entity.Playlist;
import com.musicstreaming.playlist.entity.PlaylistTrack;
import com.musicstreaming.playlist.exception.*;
import com.musicstreaming.playlist.messaging.PlaylistEventPublisher;
import com.musicstreaming.playlist.repository.PlaylistRepository;
import com.musicstreaming.playlist.repository.PlaylistTrackRepository;
import com.musicstreaming.playlist.service.PlaylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock PlaylistRepository playlistRepository;
    @Mock PlaylistTrackRepository trackRepository;
    @Mock PlaylistEventPublisher eventPublisher;

    @InjectMocks PlaylistService service;

    private static final String USER = "alice";
    private static final UUID PLAYLIST_ID = UUID.randomUUID();

    private Playlist makePlaylist(String name, boolean liked) {
        return new Playlist(USER, name, liked);
    }

    @BeforeEach
    void setUpSave() {
        lenient().when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(eventPublisher).publish(any());
    }

    // --- getPlaylists ---

    @Test
    void getPlaylists_createsLikedSongsWhenAbsent() {
        when(playlistRepository.findByUserIdAndLikedSongsTrue(USER)).thenReturn(Optional.empty());
        when(playlistRepository.findAllByUserId(USER)).thenReturn(List.of());

        service.getPlaylists(USER);

        verify(playlistRepository).save(argThat(p -> p.isLikedSongs() && USER.equals(p.getUserId())));
    }

    @Test
    void getPlaylists_doesNotDuplicateLikedSongs() {
        Playlist liked = makePlaylist(PlaylistService.LIKED_SONGS_NAME, true);
        when(playlistRepository.findByUserIdAndLikedSongsTrue(USER)).thenReturn(Optional.of(liked));
        when(playlistRepository.findAllByUserId(USER)).thenReturn(List.of(liked));

        List<PlaylistSummaryResponse> result = service.getPlaylists(USER);

        verify(playlistRepository, never()).save(argThat(Playlist::isLikedSongs));
        assertThat(result).hasSize(1);
    }

    // --- createPlaylist ---

    @Test
    void createPlaylist_happyPath() {
        PlaylistResponse result = service.createPlaylist(USER, new CreatePlaylistRequest("My Mix"));

        assertThat(result.name()).isEqualTo("My Mix");
        assertThat(result.likedSongs()).isFalse();
        verify(eventPublisher).publish(argThat(e -> "PLAYLIST_CREATED".equals(e.eventType())));
    }

    @Test
    void createPlaylist_rejectsDuplicateName() {
        when(playlistRepository.existsByUserIdAndName(USER, "My Mix")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlaylist(USER, new CreatePlaylistRequest("My Mix")))
                .isInstanceOf(PlaylistNameConflictException.class);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createPlaylist_rejectsLikedSongsName() {
        assertThatThrownBy(() -> service.createPlaylist(USER, new CreatePlaylistRequest("Liked Songs")))
                .isInstanceOf(ReservedNameException.class);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createPlaylist_rejectsLikedSongsNameCaseInsensitive() {
        assertThatThrownBy(() -> service.createPlaylist(USER, new CreatePlaylistRequest("liked songs")))
                .isInstanceOf(ReservedNameException.class);
    }

    // --- getPlaylist ---

    @Test
    void getPlaylist_returnsOwnedPlaylist() {
        Playlist p = makePlaylist("Workout", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        PlaylistResponse result = service.getPlaylist(USER, PLAYLIST_ID);

        assertThat(result.name()).isEqualTo("Workout");
    }

    @Test
    void getPlaylist_throwsWhenNotFound() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlaylist(USER, PLAYLIST_ID))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void getPlaylist_throwsWhenOwnedByOtherUser() {
        Playlist p = new Playlist("bob", "Bob's Playlist", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getPlaylist(USER, PLAYLIST_ID))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    // --- updatePlaylist ---

    @Test
    void updatePlaylist_updatesName() {
        Playlist p = makePlaylist("Old Name", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        PlaylistResponse result = service.updatePlaylist(USER, PLAYLIST_ID, new UpdatePlaylistRequest("New Name"));

        assertThat(result.name()).isEqualTo("New Name");
        verify(eventPublisher).publish(argThat(e -> "PLAYLIST_UPDATED".equals(e.eventType())));
    }

    @Test
    void updatePlaylist_rejectsLikedSongsNameOnNormalPlaylist() {
        Playlist p = makePlaylist("Normal", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.updatePlaylist(USER, PLAYLIST_ID, new UpdatePlaylistRequest("Liked Songs")))
                .isInstanceOf(ReservedNameException.class);
    }

    @Test
    void updatePlaylist_rejectsRenameOfLikedSongsPlaylist() {
        Playlist liked = makePlaylist(PlaylistService.LIKED_SONGS_NAME, true);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(liked));

        assertThatThrownBy(() -> service.updatePlaylist(USER, PLAYLIST_ID, new UpdatePlaylistRequest("Renamed")))
                .isInstanceOf(ReservedNameException.class);
    }

    // --- deletePlaylist ---

    @Test
    void deletePlaylist_happyPath() {
        Playlist p = makePlaylist("Chill", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        service.deletePlaylist(USER, PLAYLIST_ID);

        verify(playlistRepository).delete(p);
        verify(eventPublisher).publish(argThat(e -> "PLAYLIST_DELETED".equals(e.eventType())));
    }

    @Test
    void deletePlaylist_throwsForLikedSongs() {
        Playlist liked = makePlaylist(PlaylistService.LIKED_SONGS_NAME, true);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(liked));

        assertThatThrownBy(() -> service.deletePlaylist(USER, PLAYLIST_ID))
                .isInstanceOf(LikedSongsDeletionException.class);
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void deletePlaylist_throwsWhenNotFound() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePlaylist(USER, PLAYLIST_ID))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    // --- addTrack ---

    @Test
    void addTrack_appendsAtNextPosition() {
        Playlist p = makePlaylist("Mix", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(trackRepository.existsByPlaylistIdAndSongId(any(), eq("song-1"))).thenReturn(false);
        when(trackRepository.findMaxPositionByPlaylistId(any())).thenReturn(-1);

        PlaylistResponse result = service.addTrack(USER, PLAYLIST_ID, new AddTrackRequest("song-1"));

        assertThat(result.tracks()).hasSize(1);
        assertThat(result.tracks().get(0).songId()).isEqualTo("song-1");
        assertThat(result.tracks().get(0).position()).isEqualTo(0);
        verify(eventPublisher).publish(argThat(e -> "TRACK_ADDED".equals(e.eventType())));
    }

    @Test
    void addTrack_throwsOnDuplicate() {
        Playlist p = makePlaylist("Mix", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(trackRepository.existsByPlaylistIdAndSongId(any(), eq("song-1"))).thenReturn(true);

        assertThatThrownBy(() -> service.addTrack(USER, PLAYLIST_ID, new AddTrackRequest("song-1")))
                .isInstanceOf(TrackAlreadyExistsException.class);
    }

    // --- removeTrack ---

    @Test
    void removeTrack_happyPath() {
        Playlist p = makePlaylist("Mix", false);
        PlaylistTrack track = new PlaylistTrack(p, "song-1", 0);
        p.getTracks().add(track);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(trackRepository.findByPlaylistIdAndSongId(any(), eq("song-1"))).thenReturn(Optional.of(track));

        service.removeTrack(USER, PLAYLIST_ID, "song-1");

        // track is removed from the in-memory collection; orphanRemoval handles the DB delete
        assertThat(p.getTracks()).doesNotContain(track);
        verify(eventPublisher).publish(argThat(e -> "TRACK_REMOVED".equals(e.eventType())));
    }

    @Test
    void removeTrack_throwsWhenTrackNotInPlaylist() {
        Playlist p = makePlaylist("Mix", false);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));
        when(trackRepository.findByPlaylistIdAndSongId(any(), eq("song-99"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeTrack(USER, PLAYLIST_ID, "song-99"))
                .isInstanceOf(TrackNotFoundException.class);
    }

    // --- reorderTracks ---

    @Test
    void reorderTracks_updatesPositions() {
        Playlist p = makePlaylist("Mix", false);
        PlaylistTrack t1 = new PlaylistTrack(p, "song-A", 0);
        PlaylistTrack t2 = new PlaylistTrack(p, "song-B", 1);
        PlaylistTrack t3 = new PlaylistTrack(p, "song-C", 2);
        p.getTracks().addAll(List.of(t1, t2, t3));
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        // Reorder: C, A, B
        service.reorderTracks(USER, PLAYLIST_ID, new ReorderTracksRequest(List.of("song-C", "song-A", "song-B")));

        assertThat(t3.getPosition()).isEqualTo(0); // C → 0
        assertThat(t1.getPosition()).isEqualTo(1); // A → 1
        assertThat(t2.getPosition()).isEqualTo(2); // B → 2
        verify(eventPublisher).publish(argThat(e -> "TRACKS_REORDERED".equals(e.eventType())));
    }

    @Test
    void reorderTracks_throwsOnMismatch() {
        Playlist p = makePlaylist("Mix", false);
        PlaylistTrack t1 = new PlaylistTrack(p, "song-A", 0);
        p.getTracks().add(t1);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(p));

        // Provides wrong IDs
        assertThatThrownBy(() -> service.reorderTracks(USER, PLAYLIST_ID,
                new ReorderTracksRequest(List.of("song-X", "song-Y"))))
                .isInstanceOf(ReorderMismatchException.class);
    }
}
