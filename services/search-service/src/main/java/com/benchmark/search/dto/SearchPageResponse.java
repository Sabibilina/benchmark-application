package com.benchmark.search.dto;

import java.util.List;

public record SearchPageResponse(
        List<SearchResultResponse> content,
        long totalElements,
        int page,
        int size
) {
}
