package com.benchmark.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.benchmark.analytics.config.AnalyticsProperties;
import com.benchmark.analytics.dto.HistoryEventResponse;
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

    private static final String CANONICAL_USER_ID = "11111111-1111-1111-1111-111111111111";

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
        when(repository.findHistory(CANONICAL_USER_ID, 0, 20)).thenReturn(List.of(
                new AnalyticsEventRecord(UUID.randomUUID(), "play.started", CANONICAL_USER_ID, "song-1", now),
                new AnalyticsEventRecord(UUID.randomUUID(), "play.ended", CANONICAL_USER_ID, "song-1", now.plusSeconds(30))));
        when(repository.countHistory(CANONICAL_USER_ID)).thenReturn(2L);
        AnalyticsService service = new AnalyticsService(properties, repository);

        HistoryPageResponse response = service.history(CANONICAL_USER_ID, null, null);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).extracting(HistoryEventResponse::type)
                .containsExactly("play.started", "play.ended");
        assertThat(response.content().getFirst().userId()).isEqualTo(CANONICAL_USER_ID);
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

        assertThatThrownBy(() -> service.history(CANONICAL_USER_ID, -1, 20))
                .isInstanceOf(AnalyticsValidationException.class);
        assertThatThrownBy(() -> service.history(CANONICAL_USER_ID, 0, 101))
                .isInstanceOf(AnalyticsValidationException.class);
        assertThatThrownBy(() -> service.globalChart(0))
                .isInstanceOf(AnalyticsValidationException.class);
    }
}
