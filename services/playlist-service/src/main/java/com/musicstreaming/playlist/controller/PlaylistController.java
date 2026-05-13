package com.musicstreaming.playlist.controller;

import com.musicstreaming.playlist.dto.*;
import com.musicstreaming.playlist.service.PlaylistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping
    public ResponseEntity<List<PlaylistSummaryResponse>> getPlaylists(Authentication auth) {
        return ResponseEntity.ok(playlistService.getPlaylists(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<PlaylistResponse> createPlaylist(@Valid @RequestBody CreatePlaylistRequest request,
                                                           Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playlistService.createPlaylist(auth.getName(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistResponse> getPlaylist(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(playlistService.getPlaylist(auth.getName(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PlaylistResponse> updatePlaylist(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdatePlaylistRequest request,
                                                           Authentication auth) {
        return ResponseEntity.ok(playlistService.updatePlaylist(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable UUID id, Authentication auth) {
        playlistService.deletePlaylist(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tracks")
    public ResponseEntity<PlaylistResponse> addTrack(@PathVariable UUID id,
                                                     @Valid @RequestBody AddTrackRequest request,
                                                     Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playlistService.addTrack(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}/tracks/{songId}")
    public ResponseEntity<Void> removeTrack(@PathVariable UUID id,
                                            @PathVariable String songId,
                                            Authentication auth) {
        playlistService.removeTrack(auth.getName(), id, songId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/tracks/reorder")
    public ResponseEntity<PlaylistResponse> reorderTracks(@PathVariable UUID id,
                                                          @Valid @RequestBody ReorderTracksRequest request,
                                                          Authentication auth) {
        return ResponseEntity.ok(playlistService.reorderTracks(auth.getName(), id, request));
    }
}
