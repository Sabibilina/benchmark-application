package com.benchmark.streaming.controller;

import com.benchmark.streaming.dto.StreamDescriptorResponse;
import com.benchmark.streaming.messaging.PlaybackEvent;
import com.benchmark.streaming.security.UserPrincipalResolver;
import com.benchmark.streaming.service.StreamingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stream")
public class StreamingController {

    private final StreamingService streamingService;
    private final UserPrincipalResolver principalResolver;

    public StreamingController(StreamingService streamingService, UserPrincipalResolver principalResolver) {
        this.streamingService = streamingService;
        this.principalResolver = principalResolver;
    }

    @GetMapping("/{songId}")
    public StreamDescriptorResponse startStream(Authentication authentication, @PathVariable String songId) {
        return streamingService.startStream(principalResolver.resolve(authentication), songId);
    }

    @GetMapping("/{songId}/segments/{segmentIndex}")
    public ResponseEntity<byte[]> segment(@PathVariable String songId, @PathVariable int segmentIndex) {
        byte[] payload = streamingService.segment(songId, segmentIndex);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(payload.length)
                .body(payload);
    }

    @PostMapping("/{songId}/ended")
    public ResponseEntity<PlaybackEvent> ended(Authentication authentication, @PathVariable String songId) {
        return ResponseEntity.accepted().body(streamingService.ended(principalResolver.resolve(authentication), songId));
    }

    @PostMapping("/{songId}/skipped")
    public ResponseEntity<PlaybackEvent> skipped(Authentication authentication, @PathVariable String songId) {
        return ResponseEntity.accepted().body(streamingService.skipped(principalResolver.resolve(authentication), songId));
    }
}
