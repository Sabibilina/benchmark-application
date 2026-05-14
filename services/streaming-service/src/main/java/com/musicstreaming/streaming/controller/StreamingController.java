package com.musicstreaming.streaming.controller;

import com.musicstreaming.streaming.service.StreamingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stream")
public class StreamingController {

    private static final String HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl";

    private final StreamingService streamingService;

    public StreamingController(StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @GetMapping(value = "/{songId}", produces = HLS_CONTENT_TYPE)
    public ResponseEntity<String> getStream(@PathVariable String songId, Authentication auth) {
        streamingService.handleStreamStart(auth.getName(), songId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(HLS_CONTENT_TYPE))
                .body(streamingService.buildManifest(songId));
    }

    @GetMapping(value = "/{songId}/segment/{index}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getSegment(@PathVariable String songId,
                                             @PathVariable int index,
                                             Authentication auth) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(streamingService.generateSegmentPayload());
    }

    @PostMapping("/{songId}/complete")
    public ResponseEntity<Void> completeStream(@PathVariable String songId, Authentication auth) {
        streamingService.handleStreamComplete(auth.getName(), songId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{songId}/skip")
    public ResponseEntity<Void> skipStream(@PathVariable String songId, Authentication auth) {
        streamingService.handleStreamSkip(auth.getName(), songId);
        return ResponseEntity.noContent().build();
    }
}
