package com.benchmark.search.indexing;

import java.math.BigDecimal;

public record SearchDocument(
        String id,
        String title,
        String artist,
        String album,
        String genre,
        BigDecimal bpm,
        Integer year
) {
}
