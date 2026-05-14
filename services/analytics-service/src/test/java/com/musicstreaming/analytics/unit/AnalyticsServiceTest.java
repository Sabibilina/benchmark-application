package com.musicstreaming.analytics.unit;

import com.musicstreaming.analytics.dto.ChartEntry;
import com.musicstreaming.analytics.dto.HistoryEntry;
import com.musicstreaming.analytics.model.PlaybackEventRecord;
import com.musicstreaming.analytics.repository.AnalyticsRepository;
import com.musicstreaming.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.musicstreaming.analytics.service.AnalyticsService.GLOBAL_CHART_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsRepository repository;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(repository, 100);
    }

    // ── getHistory ────────────────────────────────────────────────────────────

    @Test
    void getHistory_delegatesToRepositoryWithConfiguredLimit() {
        when(repository.findHistoryForUser("alice", 100)).thenReturn(List.of());
        service.getHistory("alice");
        verify(repository).findHistoryForUser("alice", 100);
    }

    @Test
    void getHistory_returnsEmptyList_whenNoEvents() {
        when(repository.findHistoryForUser(anyString(), anyInt())).thenReturn(List.of());
        assertThat(service.getHistory("alice")).isEmpty();
    }

    @Test
    void getHistory_returnsEntriesFromRepository() {
        HistoryEntry entry = new HistoryEntry("song1", "play.started", Instant.now());
        when(repository.findHistoryForUser("alice", 100)).thenReturn(List.of(entry));
        assertThat(service.getHistory("alice")).containsExactly(entry);
    }

    // ── getGlobalCharts ───────────────────────────────────────────────────────

    @Test
    void getGlobalCharts_delegatesToRepositoryWithLimit50() {
        when(repository.findGlobalCharts(GLOBAL_CHART_LIMIT)).thenReturn(List.of());
        service.getGlobalCharts();
        verify(repository).findGlobalCharts(GLOBAL_CHART_LIMIT);
    }

    @Test
    void getGlobalCharts_returnsEmptyList_whenNoData() {
        when(repository.findGlobalCharts(GLOBAL_CHART_LIMIT)).thenReturn(List.of());
        assertThat(service.getGlobalCharts()).isEmpty();
    }

    @Test
    void getGlobalCharts_returnsRankedEntriesFromRepository() {
        ChartEntry entry = new ChartEntry(1, "song1", 42L);
        when(repository.findGlobalCharts(GLOBAL_CHART_LIMIT)).thenReturn(List.of(entry));
        assertThat(service.getGlobalCharts()).containsExactly(entry);
    }

    // ── recordEvent ───────────────────────────────────────────────────────────

    @Test
    void recordEvent_insertsToRepository_whenValidEvent() {
        PlaybackEventRecord event = new PlaybackEventRecord("play.started", "alice", "song1", Instant.now());
        service.recordEvent(event);
        verify(repository).insert(event);
    }

    @Test
    void recordEvent_skipsInsert_whenUserIdIsNull() {
        PlaybackEventRecord event = new PlaybackEventRecord("play.started", null, "song1", Instant.now());
        service.recordEvent(event);
        verify(repository, never()).insert(any());
    }

    @Test
    void recordEvent_skipsInsert_whenSongIdIsNull() {
        PlaybackEventRecord event = new PlaybackEventRecord("play.started", "alice", null, Instant.now());
        service.recordEvent(event);
        verify(repository, never()).insert(any());
    }
}
