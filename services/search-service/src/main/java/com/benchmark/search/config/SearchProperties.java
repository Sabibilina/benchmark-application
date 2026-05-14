package com.benchmark.search.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        String opensearchUrl,
        String indexName,
        Path catalogDatasetPath,
        boolean indexingEnabled,
        int indexingBatchSize,
        int defaultPageSize,
        int maxPageSize
) {
}
