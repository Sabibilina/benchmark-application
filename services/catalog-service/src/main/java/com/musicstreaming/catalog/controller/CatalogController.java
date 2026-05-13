package com.musicstreaming.catalog.controller;

import com.musicstreaming.catalog.dto.PagedResponse;
import com.musicstreaming.catalog.dto.SongResponse;
import com.musicstreaming.catalog.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/songs")
    public ResponseEntity<PagedResponse<SongResponse>> getAllSongs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return ResponseEntity.ok(catalogService.findAll(page, size, sort, direction));
    }

    @GetMapping("/songs/{id}")
    public ResponseEntity<SongResponse> getSong(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.findById(id));
    }

    @GetMapping("/artists/{artistId}/top-tracks")
    public ResponseEntity<List<SongResponse>> getArtistTopTracks(@PathVariable String artistId) {
        return ResponseEntity.ok(catalogService.findTopTracksByArtistId(artistId));
    }
}
