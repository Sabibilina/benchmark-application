package com.benchmark.catalog.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catalog")
public record CatalogDatasetProperties(
        String datasetPath,
        boolean ingestionEnabled,
        int defaultPageSize,
        int maxPageSize
) {
}
