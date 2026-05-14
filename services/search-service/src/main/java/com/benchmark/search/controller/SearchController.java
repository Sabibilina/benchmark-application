package com.benchmark.search.controller;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.dto.SearchPageResponse;
import com.benchmark.search.dto.SearchRequest;
import com.benchmark.search.service.SearchService;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;
    private final SearchProperties properties;

    public SearchController(SearchService searchService, SearchProperties properties) {
        this.searchService = searchService;
        this.properties = properties;
    }

    @GetMapping("/search")
    SearchPageResponse search(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String genre,
                              @RequestParam(name = "bpm_min", required = false) BigDecimal bpmMin,
                              @RequestParam(name = "bpm_max", required = false) BigDecimal bpmMax,
                              @RequestParam(required = false) Integer year,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        SearchRequest request = SearchRequest.of(q, genre, bpmMin, bpmMax, year, page, size, properties);
        return searchService.search(request);
    }
}
