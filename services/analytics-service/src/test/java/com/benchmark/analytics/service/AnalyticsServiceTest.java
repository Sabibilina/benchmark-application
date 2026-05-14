package com.benchmark.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.benchmark.analytics.config.AnalyticsProperties;
import com.benchmark.analytics.dto.HistoryPageResponse;
import com.benchmark.analytics.persistence.AnalyticsEventRecord;
import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import com.benchmark.analytics.persistence.GlobalChartRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalyticsServiceTest {

    private final AnalyticsProperties properties = new AnalyticsProperties(
            "jdbc:clickhouse://localhost:8123/analytics",
            "benchmark",
            "benchmark",
            "playback-events",
            20,
            100,
            50);

    @Test
    void returnsOnlyRequestedUserHistory() {
        AnalyticsEventRepository repository = Mockito.mock(AnalyticsEventRepository.class);
        Instant now = Instant.parse("2026-05-14T10:15:30Z");
        when(repository.findHistory("user-1", 0, 20)).thenReturn(List.of(
                new AnalyticsEventRecord(UUID.randomUUID(), "play.started", "user-1", "song-1", now)));
        when(repository.countHistory("user-1")).thenReturn(1L);
        AnalyticsService service = new AnalyticsService(properties, repository);

        HistoryPageResponse response = service.history("user-1", null, null);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().userId()).isEqualTo("user-1");
        assertThat(response.content().getFirst().timestamp()).isEqualTo(now);
    }

    @Test
    void computesRankedGlobalChartResponses() {
        AnalyticsEventRepository repository = Mockito.mock(AnalyticsEventRepository.class);
        when(repository.findGlobalChart(2)).thenReturn(List.of(
                new GlobalChartRecord("song-1", 7),
                new GlobalChartRecord("song-2", 3)));
        AnalyticsService service = new AnalyticsService(properties, repository);

        var chart = service.globalChart(2);

        assertThat(chart).hasSize(2);
        assertThat(chart.getFirst().rank()).isEqualTo(1);
        assertThat(chart.getFirst().playCount()).isEqualTo(7);
    }

    @Test
    void rejectsInvalidHistoryPaginationAndChartLimit() {
        AnalyticsService service = new AnalyticsService(properties, Mockito.mock(AnalyticsEventRepository.class));

        assertThatThrownBy(() -> service.history("user-1", -1, 20))
                .isInstanceOf(AnalyticsValidationException.class);
        assertThatThrownBy(() -> service.history("user-1", 0, 101))
                .isInstanceOf(AnalyticsValidationException.class);
        assertThatThrownBy(() -> service.globalChart(0))
                .isInstanceOf(AnalyticsValidationException.class);
    }
}
