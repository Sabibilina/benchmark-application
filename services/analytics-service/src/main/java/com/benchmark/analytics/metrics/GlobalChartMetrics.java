package com.benchmark.analytics.metrics;

import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import com.benchmark.analytics.persistence.GlobalChartRecord;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GlobalChartMetrics {

    private final AnalyticsEventRepository repository;
    private final MultiGauge topTrackGauge;
    private final int topTracksLimit;

    public GlobalChartMetrics(
            AnalyticsEventRepository repository,
            MeterRegistry meterRegistry,
            @Value("${app.analytics.metrics.top-tracks-limit:10}") int topTracksLimit
    ) {
        this.repository = repository;
        this.topTracksLimit = topTracksLimit;
        this.topTrackGauge = MultiGauge.builder("analytics.global.chart.play.count")
                .description("Global play.started counts for top tracks")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnApplicationReady() {
        refreshTopTrackMetrics();
    }

    @Scheduled(fixedDelayString = "${app.analytics.metrics.refresh-interval-ms:30000}")
    public void refreshTopTrackMetrics() {
        List<GlobalChartRecord> records = repository.findGlobalChart(topTracksLimit);
        if (records == null) {
            records = List.of();
        }
        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        for (GlobalChartRecord record : records) {
            rows.add(MultiGauge.Row.of(Tags.of("song_id", record.songId()), record.playCount()));
        }
        topTrackGauge.register(rows, true);
    }
}
