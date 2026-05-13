package com.benchmark.catalog.dto;

import java.util.List;

public record SongPageResponse(
        List<SongListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
