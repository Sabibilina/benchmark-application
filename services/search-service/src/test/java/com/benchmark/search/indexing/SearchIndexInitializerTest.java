package com.benchmark.search.indexing;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.opensearch.OpenSearchIndexClient;
import com.benchmark.search.support.TestDataFiles;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SearchIndexInitializerTest {

    @Test
    void createsIndexAndUpsertsCsvDocumentsWhenEnabled() {
        Path csv = TestDataFiles.catalogCsv();
        SearchProperties properties = new SearchProperties("http://localhost:9200", "songs", csv, true, 500, 20, 100);
        OpenSearchIndexClient client = Mockito.mock(OpenSearchIndexClient.class);

        new SearchIndexInitializer(properties, new CatalogCsvReader(), client).run(null);

        ArgumentCaptor<java.util.List<SearchDocument>> documents = ArgumentCaptor.forClass(java.util.List.class);
        verify(client).ensureIndex("songs");
        verify(client).upsertAll(eq("songs"), documents.capture());
        org.assertj.core.api.Assertions.assertThat(documents.getValue()).hasSize(2);
    }

    @Test
    void doesNothingWhenIndexingDisabled() {
        SearchProperties properties = new SearchProperties("http://localhost:9200", "songs", Path.of("missing.csv"), false, 500, 20, 100);
        OpenSearchIndexClient client = Mockito.mock(OpenSearchIndexClient.class);

        new SearchIndexInitializer(properties, new CatalogCsvReader(), client).run(null);

        verify(client, never()).ensureIndex("songs");
    }
}
