package com.benchmark.streaming.dto;

public record SegmentReference(
        int index,
        String url,
        int sizeBytes
) {
}
