package com.benchmark.streaming.service;

import com.benchmark.streaming.config.StreamingProperties;
import com.benchmark.streaming.dto.SegmentReference;
import com.benchmark.streaming.dto.StreamDescriptorResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class StreamDescriptorService {

    private final StreamingProperties properties;
    private final Clock clock;

    public StreamDescriptorService(StreamingProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public StreamDescriptorResponse descriptor(String songId) {
        DummySegmentService.validateSongId(songId);
        String encodedSongId = UriUtils.encodePathSegment(songId, java.nio.charset.StandardCharsets.UTF_8);
        List<SegmentReference> segments = IntStream.range(0, properties.segmentCount())
                .mapToObj(index -> new SegmentReference(
                        index,
                        "/stream/" + encodedSongId + "/segments/" + index,
                        properties.segmentSizeBytes()))
                .toList();
        return new StreamDescriptorResponse(
                songId,
                "simulated-hls-descriptor",
                properties.segmentCount(),
                properties.segmentSizeBytes(),
                Instant.now(clock),
                segments,
                "/stream/" + encodedSongId + "/ended",
                "/stream/" + encodedSongId + "/skipped");
    }
}
