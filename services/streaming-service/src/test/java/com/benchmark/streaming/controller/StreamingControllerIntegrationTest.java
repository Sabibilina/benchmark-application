package com.benchmark.streaming.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.streaming.messaging.PlaybackEventPublisher;
import com.benchmark.streaming.support.TestKeyFiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StreamingControllerIntegrationTest {

    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PlaybackEventPublisher playbackEventPublisher;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("app.streaming.playback-events-topic", () -> "playback-events");
        registry.add("app.streaming.segment-count", () -> "2");
        registry.add("app.streaming.segment-size-bytes", () -> "32");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:65534");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
    }

    @Test
    void streamingEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/stream/song-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/stream/song-1/segments/0")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/stream/song-1/ended")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/stream/song-1/skipped")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/stream/song-1").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startStreamReturnsDescriptorAndPublishesStartedEvent() throws Exception {
        mockMvc.perform(get("/stream/song-1").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songId").value("song-1"))
                .andExpect(jsonPath("$.descriptorType").value("simulated-hls-descriptor"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.segmentSizeBytes").value(32))
                .andExpect(jsonPath("$.segments", hasSize(2)))
                .andExpect(jsonPath("$.segments[0].url").value("/stream/song-1/segments/0"))
                .andExpect(jsonPath("$.endedUrl").value("/stream/song-1/ended"))
                .andExpect(jsonPath("$.skippedUrl").value("/stream/song-1/skipped"));

        verify(playbackEventPublisher).publish(argThat(event ->
                event.type().equals("play.started")
                        && event.userId().equals("user-1")
                        && event.songId().equals("song-1")
                        && event.timestamp() != null));
    }

    @Test
    void segmentEndpointReturnsConfiguredBinaryPayload() throws Exception {
        byte[] payload = mockMvc.perform(get("/stream/song-1/segments/1").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(payload).hasSize(32);
    }

    @Test
    void endedAndSkippedPublishRequiredEvents() throws Exception {
        mockMvc.perform(post("/stream/song-1/ended").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("play.ended"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.songId").value("song-1"));

        mockMvc.perform(post("/stream/song-1/skipped").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("play.skipped"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.songId").value("song-1"));

        verify(playbackEventPublisher).publish(argThat(event -> event.type().equals("play.ended")));
        verify(playbackEventPublisher).publish(argThat(event -> event.type().equals("play.skipped")));
    }

    @Test
    void invalidSegmentReturnsBadRequestAndHealthIsOperational() throws Exception {
        mockMvc.perform(get("/stream/song-1/segments/2").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
