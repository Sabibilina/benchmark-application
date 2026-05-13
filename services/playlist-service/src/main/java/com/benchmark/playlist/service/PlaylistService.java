package com.benchmark.playlist.service;

import com.benchmark.playlist.dto.AddTrackRequest;
import com.benchmark.playlist.dto.CreatePlaylistRequest;
import com.benchmark.playlist.dto.PlaylistResponse;
import com.benchmark.playlist.dto.PlaylistSummaryResponse;
import com.benchmark.playlist.dto.PlaylistTrackResponse;
import com.benchmark.playlist.dto.ReorderTracksRequest;
import com.benchmark.playlist.dto.UpdatePlaylistRequest;
import com.benchmark.playlist.entity.Playlist;
import com.benchmark.playlist.entity.PlaylistTrack;
import com.benchmark.playlist.repository.PlaylistRepository;
import com.benchmark.playlist.repository.PlaylistTrackRepository;
import com.benchmark.playlist.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final LikedSongsService likedSongsService;
    private final Clock clock;

    public PlaylistService(
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            LikedSongsService likedSongsService,
            Clock clock
    ) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.likedSongsService = likedSongsService;
        this.clock = clock;
    }

    @Transactional
    public List<PlaylistSummaryResponse> listPlaylists(AuthenticatedUser user) {
        likedSongsService.ensureLikedSongs(user.userId());
        return playlistRepository.findByOwnerUserIdOrderByLikedSongsDescCreatedAtAsc(user.userId())
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public PlaylistResponse createPlaylist(AuthenticatedUser user, CreatePlaylistRequest request) {
        Instant now = Instant.now(clock);
        Playlist playlist = playlistRepository.save(new Playlist(
                user.userId(),
                request.name().trim(),
                trimNullable(request.description()),
                false,
                now));
        return toResponse(playlist);
    }

    @Transactional(readOnly = true)
    public PlaylistResponse getPlaylist(AuthenticatedUser user, UUID id) {
        return toResponse(findOwnedWithTracks(user, id));
    }

    @Transactional
    public PlaylistResponse updatePlaylist(AuthenticatedUser user, UUID id, UpdatePlaylistRequest request) {
        Playlist playlist = findOwnedWithTracks(user, id);
        if (playlist.isLikedSongs()) {
            throw new PlaylistOperationException("Liked Songs playlist cannot be renamed");
        }
        playlist.rename(request.name().trim(), trimNullable(request.description()), Instant.now(clock));
        return toResponse(playlist);
    }

    @Transactional
    public void deletePlaylist(AuthenticatedUser user, UUID id) {
        Playlist playlist = findOwnedWithTracks(user, id);
        if (playlist.isLikedSongs()) {
            throw new PlaylistOperationException("Liked Songs playlist cannot be deleted");
        }
        playlistRepository.delete(playlist);
    }

    @Transactional
    public AddTrackResult addTrack(AuthenticatedUser user, UUID playlistId, AddTrackRequest request) {
        Playlist playlist = findOwnedWithTracks(user, playlistId);
        String songId = request.songId().trim();
        return playlist.getTracks().stream()
                .filter(track -> track.getSongId().equals(songId))
                .findFirst()
                .map(track -> new AddTrackResult(toTrackResponse(track), false))
                .orElseGet(() -> {
                    Instant now = Instant.now(clock);
                    int nextPosition = playlist.getTracks().stream()
                            .mapToInt(PlaylistTrack::getPosition)
                            .max()
                            .orElse(-1) + 1;
                    PlaylistTrack track = new PlaylistTrack(playlist, songId, nextPosition, now);
                    playlist.addTrack(track, now);
                    playlistTrackRepository.save(track);
                    return new AddTrackResult(toTrackResponse(track), true);
                });
    }

    @Transactional
    public void removeTrack(AuthenticatedUser user, UUID playlistId, String songId) {
        Playlist playlist = findOwnedWithTracks(user, playlistId);
        PlaylistTrack track = playlist.getTracks().stream()
                .filter(candidate -> candidate.getSongId().equals(songId))
                .findFirst()
                .orElseThrow(PlaylistTrackNotFoundException::new);
        playlist.removeTrack(track, Instant.now(clock));
        compactPositions(playlist.getTracks());
    }

    @Transactional
    public PlaylistResponse reorderTracks(AuthenticatedUser user, UUID playlistId, ReorderTracksRequest request) {
        Playlist playlist = findOwnedWithTracks(user, playlistId);
        List<String> requestedSongIds = request.songIds().stream()
                .map(String::trim)
                .toList();
        if (requestedSongIds.stream().anyMatch(String::isBlank)) {
            throw new PlaylistOperationException("Reorder song ids must not be blank");
        }
        if (new HashSet<>(requestedSongIds).size() != requestedSongIds.size()) {
            throw new PlaylistOperationException("Reorder song ids must not contain duplicates");
        }
        Map<String, PlaylistTrack> tracksBySong = playlist.getTracks().stream()
                .collect(Collectors.toMap(PlaylistTrack::getSongId, Function.identity()));
        if (!tracksBySong.keySet().equals(new HashSet<>(requestedSongIds))) {
            throw new PlaylistOperationException("Reorder request must include exactly the playlist song ids");
        }
        for (int i = 0; i < requestedSongIds.size(); i++) {
            tracksBySong.get(requestedSongIds.get(i)).setPosition(i);
        }
        playlist.touch(Instant.now(clock));
        return toResponse(playlist);
    }

    private Playlist findOwnedWithTracks(AuthenticatedUser user, UUID id) {
        return playlistRepository.findWithTracksByIdAndOwnerUserId(id, user.userId())
                .orElseThrow(PlaylistNotFoundException::new);
    }

    private void compactPositions(List<PlaylistTrack> tracks) {
        List<PlaylistTrack> orderedTracks = new ArrayList<>(tracks);
        orderedTracks.sort(Comparator.comparingInt(PlaylistTrack::getPosition));
        for (int i = 0; i < orderedTracks.size(); i++) {
            orderedTracks.get(i).setPosition(i);
        }
    }

    private PlaylistSummaryResponse toSummary(Playlist playlist) {
        return new PlaylistSummaryResponse(
                playlist.getId(),
                playlist.getOwnerUserId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.isLikedSongs(),
                playlist.getTracks().size(),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt());
    }

    private PlaylistResponse toResponse(Playlist playlist) {
        List<PlaylistTrackResponse> tracks = playlist.getTracks().stream()
                .sorted(Comparator.comparingInt(PlaylistTrack::getPosition))
                .map(this::toTrackResponse)
                .toList();
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getOwnerUserId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.isLikedSongs(),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt(),
                tracks);
    }

    private PlaylistTrackResponse toTrackResponse(PlaylistTrack track) {
        return new PlaylistTrackResponse(track.getId(), track.getSongId(), track.getPosition(), track.getAddedAt());
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
