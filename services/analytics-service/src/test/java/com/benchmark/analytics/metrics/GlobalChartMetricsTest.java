package com.benchmark.analytics.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import com.benchmark.analytics.persistence.GlobalChartRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GlobalChartMetricsTest {

    @Test
    void publishesGlobalTopTrackPlayCountsForPrometheusScraping() {
        AnalyticsEventRepository repository = Mockito.mock(AnalyticsEventRepository.class);
        when(repository.findGlobalChart(2)).thenReturn(List.of(
                new GlobalChartRecord("song-1", 7),
                new GlobalChartRecord("song-2", 3)));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GlobalChartMetrics metrics = new GlobalChartMetrics(repository, meterRegistry, 2);

        metrics.refreshTopTrackMetrics();

        assertThat(meterRegistry.get("analytics.global.chart.play.count")
                .tag("song_id", "song-1")
                .gauge()
                .value()).isEqualTo(7.0);
        assertThat(meterRegistry.get("analytics.global.chart.play.count")
                .tag("song_id", "song-2")
                .gauge()
                .value()).isEqualTo(3.0);
        verify(repository).findGlobalChart(2);
    }
}
