package com.benchmark.analytics.dto;

import java.util.List;

public record HistoryPageResponse(
        List<HistoryEventResponse> content,
        long totalElements,
        int page,
        int size
) {
}
