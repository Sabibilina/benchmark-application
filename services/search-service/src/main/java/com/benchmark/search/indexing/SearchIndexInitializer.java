package com.benchmark.search.indexing;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.opensearch.OpenSearchIndexClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final SearchProperties properties;
    private final CatalogCsvReader csvReader;
    private final OpenSearchIndexClient indexClient;

    public SearchIndexInitializer(SearchProperties properties, CatalogCsvReader csvReader, OpenSearchIndexClient indexClient) {
        this.properties = properties;
        this.csvReader = csvReader;
        this.indexClient = indexClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.indexingEnabled()) {
            LOGGER.info("Search startup indexing is disabled");
            return;
        }
        LOGGER.info("Preparing OpenSearch index {}", properties.indexName());
        indexClient.ensureIndex(properties.indexName());
        List<SearchDocument> batch = new ArrayList<>(properties.indexingBatchSize());
        AtomicInteger indexed = new AtomicInteger();
        csvReader.forEachDocument(properties.catalogDatasetPath(), document -> {
            batch.add(document);
            if (batch.size() >= properties.indexingBatchSize()) {
                flush(batch, indexed);
            }
        });
        flush(batch, indexed);
        if (indexed.get() == 0) {
            throw new IllegalStateException("Catalog dataset is empty: " + properties.catalogDatasetPath());
        }
        LOGGER.info("Indexed {} catalog documents into OpenSearch index {}", indexed.get(), properties.indexName());
    }

    private void flush(List<SearchDocument> batch, AtomicInteger indexed) {
        if (batch.isEmpty()) {
            return;
        }
        indexClient.upsertAll(properties.indexName(), List.copyOf(batch));
        indexed.addAndGet(batch.size());
        batch.clear();
    }
}
