package com.benchmark.analytics.service;

import com.benchmark.analytics.config.AnalyticsProperties;
import com.benchmark.analytics.dto.GlobalChartItemResponse;
import com.benchmark.analytics.dto.HistoryEventResponse;
import com.benchmark.analytics.dto.HistoryPageResponse;
import com.benchmark.analytics.persistence.AnalyticsEventRecord;
import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import com.benchmark.analytics.persistence.GlobalChartRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final AnalyticsProperties properties;
    private final AnalyticsEventRepository repository;

    public AnalyticsService(AnalyticsProperties properties, AnalyticsEventRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public HistoryPageResponse history(String userId, Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? properties.defaultPageSize() : size;
        validatePage(resolvedPage, resolvedSize);
        List<HistoryEventResponse> content = repository.findHistory(userId, resolvedPage, resolvedSize).stream()
                .map(this::toHistoryResponse)
                .toList();
        return new HistoryPageResponse(content, repository.countHistory(userId), resolvedPage, resolvedSize);
    }

    public List<GlobalChartItemResponse> globalChart(Integer limit) {
        int resolvedLimit = limit == null ? properties.globalChartLimit() : limit;
        if (resolvedLimit < 1 || resolvedLimit > properties.maxPageSize()) {
            throw new AnalyticsValidationException("limit must be between 1 and " + properties.maxPageSize());
        }
        List<GlobalChartRecord> records = repository.findGlobalChart(resolvedLimit);
        List<GlobalChartItemResponse> responses = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            GlobalChartRecord record = records.get(i);
            responses.add(new GlobalChartItemResponse(record.songId(), record.playCount(), i + 1));
        }
        return responses;
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new AnalyticsValidationException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > properties.maxPageSize()) {
            throw new AnalyticsValidationException("size must be between 1 and " + properties.maxPageSize());
        }
    }

    private HistoryEventResponse toHistoryResponse(AnalyticsEventRecord record) {
        return new HistoryEventResponse(
                record.eventId(),
                record.eventType(),
                record.userId(),
                record.songId(),
                record.occurredAt());
    }
}
