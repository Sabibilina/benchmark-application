package com.musicstreaming.analytics.unit;

import com.musicstreaming.analytics.messaging.BatchEventBuffer;
import com.musicstreaming.analytics.model.PlaybackEventRecord;
import com.musicstreaming.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchEventBufferTest {

    @Mock
    private AnalyticsService analyticsService;

    private BatchEventBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new BatchEventBuffer(analyticsService, 3);
    }

    private PlaybackEventRecord event(String userId, String songId) {
        return new PlaybackEventRecord("play.started", userId, songId, Instant.now());
    }

    @Test
    void add_doesNotFlush_whenBelowBatchSize() {
        buffer.add(event("user1", "song1"));
        buffer.add(event("user2", "song2"));
        verifyNoInteractions(analyticsService);
    }

    @Test
    void add_flushes_whenBatchSizeReached() {
        buffer.add(event("user1", "song1"));
        buffer.add(event("user2", "song2"));
        buffer.add(event("user3", "song3"));

        verify(analyticsService, times(1)).recordBatch(argThat(list -> list.size() == 3));
    }

    @Test
    void add_clearsBufferAfterFlush() {
        buffer.add(event("user1", "song1"));
        buffer.add(event("user2", "song2"));
        buffer.add(event("user3", "song3"));
        reset(analyticsService);

        buffer.add(event("user4", "song4"));
        verifyNoInteractions(analyticsService);
    }

    @Test
    void add_skipsEventsWithNullUserId() {
        PlaybackEventRecord nullUser = new PlaybackEventRecord("play.started", null, "song1", Instant.now());
        buffer.add(nullUser);
        buffer.add(event("user2", "song2"));
        buffer.add(event("user3", "song3"));
        buffer.add(event("user4", "song4"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlaybackEventRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(analyticsService).recordBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue()).noneMatch(e -> e.userId() == null);
    }

    @Test
    void add_skipsEventsWithNullSongId() {
        PlaybackEventRecord nullSong = new PlaybackEventRecord("play.started", "user1", null, Instant.now());
        buffer.add(nullSong);
        buffer.add(event("user2", "song2"));
        buffer.add(event("user3", "song3"));
        buffer.add(event("user4", "song4"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlaybackEventRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(analyticsService).recordBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue()).noneMatch(e -> e.songId() == null);
    }

    @Test
    void scheduledFlush_doesNothing_whenBufferIsEmpty() {
        buffer.scheduledFlush();
        verifyNoInteractions(analyticsService);
    }

    @Test
    void scheduledFlush_drainsPendingEvents() {
        buffer.add(event("user1", "song1"));
        buffer.add(event("user2", "song2"));

        buffer.scheduledFlush();

        verify(analyticsService, times(1)).recordBatch(argThat(list -> list.size() == 2));
    }

    @Test
    void scheduledFlush_clearsBufferAfterDrain() {
        buffer.add(event("user1", "song1"));
        buffer.scheduledFlush();
        reset(analyticsService);

        buffer.scheduledFlush();
        verifyNoInteractions(analyticsService);
    }
}
