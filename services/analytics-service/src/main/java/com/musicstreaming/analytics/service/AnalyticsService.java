package com.musicstreaming.analytics.service;

import com.musicstreaming.analytics.dto.ChartEntry;
import com.musicstreaming.analytics.dto.HistoryEntry;
import com.musicstreaming.analytics.model.PlaybackEventRecord;
import com.musicstreaming.analytics.repository.AnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    public static final int GLOBAL_CHART_LIMIT = 50;

    private final AnalyticsRepository repository;
    private final int historyLimit;

    public AnalyticsService(AnalyticsRepository repository,
                            @Value("${analytics.history.limit:100}") int historyLimit) {
        this.repository = repository;
        this.historyLimit = historyLimit;
    }

    public void recordEvent(PlaybackEventRecord event) {
        if (event.userId() == null || event.songId() == null) {
            log.warn("Skipping playback event with null userId or songId: type={}", event.type());
            return;
        }
        repository.insert(event);
    }

    public List<HistoryEntry> getHistory(String userId) {
        return repository.findHistoryForUser(userId, historyLimit);
    }

    public List<ChartEntry> getGlobalCharts() {
        return repository.findGlobalCharts(GLOBAL_CHART_LIMIT);
    }
}
