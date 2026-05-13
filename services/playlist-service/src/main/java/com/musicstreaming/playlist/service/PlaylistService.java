package com.musicstreaming.playlist.service;

import com.musicstreaming.playlist.dto.*;
import com.musicstreaming.playlist.entity.Playlist;
import com.musicstreaming.playlist.entity.PlaylistTrack;
import com.musicstreaming.playlist.event.PlaylistEvent;
import com.musicstreaming.playlist.exception.*;
import com.musicstreaming.playlist.messaging.PlaylistEventPublisher;
import com.musicstreaming.playlist.repository.PlaylistRepository;
import com.musicstreaming.playlist.repository.PlaylistTrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PlaylistService {

    public static final String LIKED_SONGS_NAME = "Liked Songs";

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository trackRepository;
    private final PlaylistEventPublisher eventPublisher;

    public PlaylistService(PlaylistRepository playlistRepository,
                           PlaylistTrackRepository trackRepository,
                           PlaylistEventPublisher eventPublisher) {
        this.playlistRepository = playlistRepository;
        this.trackRepository = trackRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public List<PlaylistSummaryResponse> getPlaylists(String userId) {
        ensureLikedSongsExists(userId);
        return playlistRepository.findAllByUserId(userId).stream()
                .map(PlaylistSummaryResponse::from)
                .toList();
    }

    @Transactional
    public PlaylistResponse createPlaylist(String userId, CreatePlaylistRequest request) {
        if (LIKED_SONGS_NAME.equalsIgnoreCase(request.name())) {
            throw new ReservedNameException(request.name());
        }
        if (playlistRepository.existsByUserIdAndName(userId, request.name())) {
            throw new PlaylistNameConflictException(request.name());
        }
        Playlist playlist = new Playlist(userId, request.name(), false);
        playlistRepository.save(playlist);
        eventPublisher.publish(PlaylistEvent.of("PLAYLIST_CREATED", playlist.getId(), userId, playlist.getName()));
        return PlaylistResponse.from(playlist);
    }

    @Transactional(readOnly = true)
    public PlaylistResponse getPlaylist(String userId, UUID id) {
        Playlist playlist = resolveOwned(userId, id);
        return PlaylistResponse.from(playlist);
    }

    @Transactional
    public PlaylistResponse updatePlaylist(String userId, UUID id, UpdatePlaylistRequest request) {
        Playlist playlist = resolveOwned(userId, id);
        if (LIKED_SONGS_NAME.equalsIgnoreCase(request.name()) && !playlist.isLikedSongs()) {
            throw new ReservedNameException(request.name());
        }
        if (playlist.isLikedSongs()) {
            throw new ReservedNameException(LIKED_SONGS_NAME);
        }
        playlist.setName(request.name());
        playlistRepository.save(playlist);
        eventPublisher.publish(PlaylistEvent.of("PLAYLIST_UPDATED", playlist.getId(), userId, playlist.getName()));
        return PlaylistResponse.from(playlist);
    }

    @Transactional
    public void deletePlaylist(String userId, UUID id) {
        Playlist playlist = resolveOwned(userId, id);
        if (playlist.isLikedSongs()) {
            throw new LikedSongsDeletionException();
        }
        playlistRepository.delete(playlist);
        eventPublisher.publish(PlaylistEvent.of("PLAYLIST_DELETED", id, userId, playlist.getName()));
    }

    @Transactional
    public PlaylistResponse addTrack(String userId, UUID playlistId, AddTrackRequest request) {
        Playlist playlist = resolveOwned(userId, playlistId);
        if (trackRepository.existsByPlaylistIdAndSongId(playlistId, request.songId())) {
            throw new TrackAlreadyExistsException(request.songId());
        }
        int nextPosition = trackRepository.findMaxPositionByPlaylistId(playlistId) + 1;
        PlaylistTrack track = new PlaylistTrack(playlist, request.songId(), nextPosition);
        playlist.getTracks().add(track);
        playlistRepository.save(playlist);
        eventPublisher.publish(PlaylistEvent.of("TRACK_ADDED", playlistId, userId, playlist.getName()));
        return PlaylistResponse.from(playlist);
    }

    @Transactional
    public void removeTrack(String userId, UUID playlistId, String songId) {
        Playlist playlist = resolveOwned(userId, playlistId);
        PlaylistTrack track = trackRepository.findByPlaylistIdAndSongId(playlistId, songId)
                .orElseThrow(() -> new TrackNotFoundException(songId));
        // Remove from collection; orphanRemoval = true deletes the row
        playlist.getTracks().remove(track);
        // Compact positions to keep them sequential
        List<PlaylistTrack> remaining = playlist.getTracks().stream()
                .sorted(Comparator.comparingInt(PlaylistTrack::getPosition))
                .toList();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        playlistRepository.save(playlist);
        eventPublisher.publish(PlaylistEvent.of("TRACK_REMOVED", playlistId, userId, playlist.getName()));
    }

    @Transactional
    public PlaylistResponse reorderTracks(String userId, UUID playlistId, ReorderTracksRequest request) {
        Playlist playlist = resolveOwned(userId, playlistId);
        List<PlaylistTrack> tracks = playlist.getTracks();

        Set<String> currentSongIds = new HashSet<>();
        for (PlaylistTrack t : tracks) {
            currentSongIds.add(t.getSongId());
        }
        Set<String> requestedSongIds = new HashSet<>(request.songIds());
        if (!currentSongIds.equals(requestedSongIds)) {
            throw new ReorderMismatchException();
        }

        Map<String, PlaylistTrack> trackBySongId = new HashMap<>();
        for (PlaylistTrack t : tracks) {
            trackBySongId.put(t.getSongId(), t);
        }

        List<String> orderedIds = request.songIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            trackBySongId.get(orderedIds.get(i)).setPosition(i);
        }
        playlistRepository.save(playlist);
        eventPublisher.publish(PlaylistEvent.of("TRACKS_REORDERED", playlistId, userId, playlist.getName()));
        return PlaylistResponse.from(playlist);
    }

    private Playlist resolveOwned(String userId, UUID id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new PlaylistNotFoundException(id));
        if (!playlist.getUserId().equals(userId)) {
            // Return 404 to avoid leaking existence of other users' playlists
            throw new PlaylistNotFoundException(id);
        }
        return playlist;
    }

    private void ensureLikedSongsExists(String userId) {
        if (playlistRepository.findByUserIdAndLikedSongsTrue(userId).isEmpty()) {
            Playlist likedSongs = new Playlist(userId, LIKED_SONGS_NAME, true);
            playlistRepository.save(likedSongs);
        }
    }
}
