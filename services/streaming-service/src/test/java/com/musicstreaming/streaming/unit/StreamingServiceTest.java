package com.musicstreaming.streaming.unit;

import com.musicstreaming.streaming.dto.PlaybackEvent;
import com.musicstreaming.streaming.event.PlaybackEventPublisher;
import com.musicstreaming.streaming.service.StreamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StreamingServiceTest {

    @Mock
    private PlaybackEventPublisher publisher;

    private StreamingService service;

    @BeforeEach
    void setUp() {
        service = new StreamingService(publisher, 4096, 5);
    }

    @Test
    void buildManifest_startsWithExtM3U() {
        String manifest = service.buildManifest("song-1");
        assertThat(manifest).startsWith("#EXTM3U");
    }

    @Test
    void buildManifest_containsConfiguredSegmentCount() {
        String manifest = service.buildManifest("song-1");
        long count = manifest.lines().filter(l -> l.startsWith("/stream/")).count();
        assertThat(count).isEqualTo(5);
    }

    @Test
    void buildManifest_segmentUrlsContainSongId() {
        String manifest = service.buildManifest("my-song-id");
        assertThat(manifest).contains("/stream/my-song-id/segment/");
    }

    @Test
    void buildManifest_endsWithEndList() {
        String manifest = service.buildManifest("song-1");
        assertThat(manifest.trim()).endsWith("#EXT-X-ENDLIST");
    }

    @Test
    void generateSegmentPayload_returnsConfiguredByteLength() {
        byte[] payload = service.generateSegmentPayload();
        assertThat(payload).hasSize(4096);
    }

    @Test
    void handleStreamStart_publishesPlayStartedEvent() {
        ArgumentCaptor<PlaybackEvent> captor = ArgumentCaptor.forClass(PlaybackEvent.class);

        service.handleStreamStart("user1", "song-1");

        verify(publisher).publish(captor.capture());
        PlaybackEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(StreamingService.EVENT_STARTED);
        assertThat(event.userId()).isEqualTo("user1");
        assertThat(event.songId()).isEqualTo("song-1");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void handleStreamComplete_publishesPlayEndedEvent() {
        ArgumentCaptor<PlaybackEvent> captor = ArgumentCaptor.forClass(PlaybackEvent.class);

        service.handleStreamComplete("user1", "song-1");

        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(StreamingService.EVENT_ENDED);
        assertThat(captor.getValue().userId()).isEqualTo("user1");
        assertThat(captor.getValue().songId()).isEqualTo("song-1");
    }

    @Test
    void handleStreamSkip_publishesPlaySkippedEvent() {
        ArgumentCaptor<PlaybackEvent> captor = ArgumentCaptor.forClass(PlaybackEvent.class);

        service.handleStreamSkip("user1", "song-1");

        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(StreamingService.EVENT_SKIPPED);
        assertThat(captor.getValue().userId()).isEqualTo("user1");
        assertThat(captor.getValue().songId()).isEqualTo("song-1");
    }
}
