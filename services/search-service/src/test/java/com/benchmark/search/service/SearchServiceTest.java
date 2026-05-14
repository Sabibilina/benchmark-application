package com.benchmark.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.dto.SearchPageResponse;
import com.benchmark.search.dto.SearchRequest;
import com.benchmark.search.indexing.SearchDocument;
import com.benchmark.search.opensearch.OpenSearchIndexClient;
import com.benchmark.search.opensearch.OpenSearchQueryBuilder;
import com.benchmark.search.opensearch.SearchHit;
import com.benchmark.search.opensearch.SearchResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SearchServiceTest {

    private final SearchProperties properties = new SearchProperties(
            "http://localhost:9200", "songs", Path.of("catalog.csv"), true, 500, 20, 100);

    @Test
    void mapsOpenSearchHitsToResponse() {
        OpenSearchIndexClient client = Mockito.mock(OpenSearchIndexClient.class);
        SearchDocument document = new SearchDocument("song-1", "Ocean Drive", "Duke Dumont", "Blase Boys Club", "dance", new BigDecimal("115.5"), 2015);
        when(client.search(eq("songs"), anyMap())).thenReturn(new SearchResult(List.of(new SearchHit(document, 3.2)), 1));
        SearchService service = new SearchService(properties, client, new OpenSearchQueryBuilder());

        SearchPageResponse response = service.search(SearchRequest.of("ocean", null, null, null, null, 0, 20, properties));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo("song-1");
        assertThat(response.content().getFirst().score()).isEqualTo(3.2);
    }

    @Test
    void requestValidationRejectsInvalidValues() {
        assertThatThrownBy(() -> SearchRequest.of(null, null, new BigDecimal("130"), new BigDecimal("120"), null, 0, 20, properties))
                .isInstanceOf(SearchValidationException.class)
                .hasMessageContaining("bpm_min");
        assertThatThrownBy(() -> SearchRequest.of(null, null, new BigDecimal("-1"), null, null, 0, 20, properties))
                .isInstanceOf(SearchValidationException.class);
        assertThatThrownBy(() -> SearchRequest.of(null, null, null, null, null, -1, 20, properties))
                .isInstanceOf(SearchValidationException.class);
        assertThatThrownBy(() -> SearchRequest.of(null, null, null, null, null, 0, 101, properties))
                .isInstanceOf(SearchValidationException.class);
    }
}
