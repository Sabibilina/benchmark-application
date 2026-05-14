package com.benchmark.search.dto;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.service.SearchValidationException;
import java.math.BigDecimal;

public record SearchRequest(
        String q,
        String genre,
        BigDecimal bpmMin,
        BigDecimal bpmMax,
        Integer year,
        int page,
        int size
) {
    public static SearchRequest of(String q,
                                   String genre,
                                   BigDecimal bpmMin,
                                   BigDecimal bpmMax,
                                   Integer year,
                                   Integer page,
                                   Integer size,
                                   SearchProperties properties) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? properties.defaultPageSize() : size;
        if (resolvedPage < 0) {
            throw new SearchValidationException("page must be greater than or equal to 0");
        }
        if (resolvedSize < 1 || resolvedSize > properties.maxPageSize()) {
            throw new SearchValidationException("size must be between 1 and " + properties.maxPageSize());
        }
        if (bpmMin != null && bpmMin.signum() < 0) {
            throw new SearchValidationException("bpm_min must be greater than or equal to 0");
        }
        if (bpmMax != null && bpmMax.signum() < 0) {
            throw new SearchValidationException("bpm_max must be greater than or equal to 0");
        }
        if (bpmMin != null && bpmMax != null && bpmMin.compareTo(bpmMax) > 0) {
            throw new SearchValidationException("bpm_min must be less than or equal to bpm_max");
        }
        if (year != null && year < 0) {
            throw new SearchValidationException("year must be greater than or equal to 0");
        }
        return new SearchRequest(blankToNull(q), blankToNull(genre), bpmMin, bpmMax, year, resolvedPage, resolvedSize);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
