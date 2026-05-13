package com.benchmark.catalog.controller;

import com.benchmark.catalog.dto.SongDetailResponse;
import com.benchmark.catalog.dto.SongPageResponse;
import com.benchmark.catalog.service.CatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/catalog/songs")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public SongPageResponse listSongs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size
    ) {
        return catalogService.listSongs(page, size);
    }

    @GetMapping("/{id}")
    public SongDetailResponse getSong(@PathVariable String id) {
        return catalogService.getSong(id);
    }
}
