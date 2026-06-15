package com.musicstreaming.analytics.messaging;

import com.musicstreaming.analytics.model.PlaybackEventRecord;
import com.musicstreaming.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BatchEventBuffer {

    private static final Logger log = LoggerFactory.getLogger(BatchEventBuffer.class);

    private final List<PlaybackEventRecord> buffer = new ArrayList<>();
    private final AnalyticsService analyticsService;
    private final int batchSize;

    public BatchEventBuffer(AnalyticsService analyticsService,
                            @Value("${analytics.batch.size:500}") int batchSize) {
        this.analyticsService = analyticsService;
        this.batchSize = batchSize;
    }

    public void add(PlaybackEventRecord event) {
        if (event.userId() == null || event.songId() == null) {
            return;
        }
        synchronized (buffer) {
            buffer.add(event);
            if (buffer.size() >= batchSize) {
                drainAndFlush();
            }
        }
    }

    @Scheduled(fixedDelayString = "${analytics.batch.flush-interval-ms:5000}")
    public void scheduledFlush() {
        drainAndFlush();
    }

    private void drainAndFlush() {
        List<PlaybackEventRecord> batch;
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        try {
            analyticsService.recordBatch(batch);
        } catch (Exception e) {
            log.error("Failed to flush batch of {} events to ClickHouse: {}", batch.size(), e.getMessage(), e);
        }
    }
}
