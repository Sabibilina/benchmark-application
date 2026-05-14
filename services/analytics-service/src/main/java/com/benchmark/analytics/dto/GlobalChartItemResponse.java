package com.benchmark.analytics.dto;

public record GlobalChartItemResponse(
        String songId,
        long playCount,
        int rank
) {
}
