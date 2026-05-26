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

/**
 * Accumulates PlaybackEventRecords from the Kafka consumer and flushes them
 * to ClickHouse in batches.  This is critical for ClickHouse performance:
 * individual row inserts create one MergeTree part per insert, causing
 * "Too many parts" errors under high throughput.  Batching collapses
 * thousands of events into a single INSERT VALUES (...),(...)... statement.
 *
 * Flush triggers:
 *   1. Buffer reaches BATCH_SIZE events (size-based, prevents memory growth)
 *   2. Scheduled timer fires every flush-interval-ms (time-based, bounds latency)
 *
 * Thread safety: all public methods are synchronized.  The Kafka consumer and
 * the scheduler both call into this class from different threads.
 */
@Component
public class BatchEventBuffer {

    private static final Logger log = LoggerFactory.getLogger(BatchEventBuffer.class);

    private final int batchSize;
    private final List<PlaybackEventRecord> buffer;
    private final AnalyticsService analyticsService;

    public BatchEventBuffer(
            AnalyticsService analyticsService,
            @Value("${analytics.batch.size:500}") int batchSize) {
        this.analyticsService = analyticsService;
        this.batchSize = batchSize;
        this.buffer = new ArrayList<>(batchSize);
    }

    public synchronized void add(PlaybackEventRecord event) {
        buffer.add(event);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${analytics.batch.flush-interval-ms:5000}")
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        List<PlaybackEventRecord> batch = new ArrayList<>(buffer);
        buffer.clear();
        try {
            analyticsService.recordEvents(batch);
            log.debug("Flushed {} events to ClickHouse", batch.size());
        } catch (Exception e) {
            log.error("Batch flush failed for {} events: {}", batch.size(), e.getMessage());
        }
    }
}
