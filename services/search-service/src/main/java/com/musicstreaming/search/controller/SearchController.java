package com.musicstreaming.search.controller;

import com.musicstreaming.search.dto.SearchRequest;
import com.musicstreaming.search.dto.SongSearchResult;
import com.musicstreaming.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<List<SongSearchResult>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(name = "bpm_min", required = false) Double bpmMin,
            @RequestParam(name = "bpm_max", required = false) Double bpmMax,
            @RequestParam(required = false) Integer year) throws IOException {

        SearchRequest request = new SearchRequest(q, genre, bpmMin, bpmMax, year);
        List<SongSearchResult> results = searchService.search(request);
        return ResponseEntity.ok(results);
    }
}
