package com.benchmark.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public record SongDetailResponse(
        String id,
        String title,
        String artist,
        String album,
        String genre,
        BigDecimal bpm,
        Integer releaseYear,
        LocalDate releaseDate,
        Integer popularity,
        Integer durationMs,
        Map<String, String> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
