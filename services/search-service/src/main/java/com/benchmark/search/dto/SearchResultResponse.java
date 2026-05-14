package com.benchmark.search.dto;

import java.math.BigDecimal;

public record SearchResultResponse(
        String id,
        String title,
        String artist,
        String album,
        String genre,
        BigDecimal bpm,
        Integer year,
        double score
) {
}
