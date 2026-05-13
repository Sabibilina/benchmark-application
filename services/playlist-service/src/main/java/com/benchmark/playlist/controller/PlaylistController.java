package com.benchmark.playlist.controller;

import com.benchmark.playlist.dto.AddTrackRequest;
import com.benchmark.playlist.dto.CreatePlaylistRequest;
import com.benchmark.playlist.dto.PlaylistResponse;
import com.benchmark.playlist.dto.PlaylistSummaryResponse;
import com.benchmark.playlist.dto.PlaylistTrackResponse;
import com.benchmark.playlist.dto.ReorderTracksRequest;
import com.benchmark.playlist.dto.UpdatePlaylistRequest;
import com.benchmark.playlist.security.UserPrincipalResolver;
import com.benchmark.playlist.service.AddTrackResult;
import com.benchmark.playlist.service.PlaylistService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final UserPrincipalResolver principalResolver;

    public PlaylistController(PlaylistService playlistService, UserPrincipalResolver principalResolver) {
        this.playlistService = playlistService;
        this.principalResolver = principalResolver;
    }

    @GetMapping
    public List<PlaylistSummaryResponse> list(Authentication authentication) {
        return playlistService.listPlaylists(principalResolver.resolve(authentication));
    }

    @PostMapping
    public ResponseEntity<PlaylistResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreatePlaylistRequest request
    ) {
        PlaylistResponse playlist = playlistService.createPlaylist(principalResolver.resolve(authentication), request);
        return ResponseEntity.created(URI.create("/playlists/" + playlist.id())).body(playlist);
    }

    @GetMapping("/{id}")
    public PlaylistResponse get(Authentication authentication, @PathVariable UUID id) {
        return playlistService.getPlaylist(principalResolver.resolve(authentication), id);
    }

    @PatchMapping("/{id}")
    public PlaylistResponse update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlaylistRequest request
    ) {
        return playlistService.updatePlaylist(principalResolver.resolve(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        playlistService.deletePlaylist(principalResolver.resolve(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tracks")
    public ResponseEntity<PlaylistTrackResponse> addTrack(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AddTrackRequest request
    ) {
        AddTrackResult result = playlistService.addTrack(principalResolver.resolve(authentication), id, request);
        ResponseEntity.BodyBuilder builder = result.created()
                ? ResponseEntity.created(URI.create("/playlists/" + id + "/tracks/" + result.track().songId()))
                : ResponseEntity.ok();
        return builder.body(result.track());
    }

    @DeleteMapping("/{id}/tracks/{songId}")
    public ResponseEntity<Void> removeTrack(
            Authentication authentication,
            @PathVariable UUID id,
            @PathVariable String songId
    ) {
        playlistService.removeTrack(principalResolver.resolve(authentication), id, songId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/tracks/reorder")
    public PlaylistResponse reorderTracks(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ReorderTracksRequest request
    ) {
        return playlistService.reorderTracks(principalResolver.resolve(authentication), id, request);
    }
}
