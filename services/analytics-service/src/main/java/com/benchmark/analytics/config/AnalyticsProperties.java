package com.benchmark.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analytics")
public record AnalyticsProperties(
        String clickhouseUrl,
        String clickhouseUser,
        String clickhousePassword,
        String playbackEventsTopic,
        int defaultPageSize,
        int maxPageSize,
        int globalChartLimit
) {
}
