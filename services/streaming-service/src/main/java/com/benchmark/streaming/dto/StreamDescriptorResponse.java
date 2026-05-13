package com.benchmark.streaming.dto;

import java.time.Instant;
import java.util.List;

public record StreamDescriptorResponse(
        String songId,
        String descriptorType,
        int segmentCount,
        int segmentSizeBytes,
        Instant issuedAt,
        List<SegmentReference> segments,
        String endedUrl,
        String skippedUrl
) {
}
