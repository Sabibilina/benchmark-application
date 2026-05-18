package com.benchmark.search.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.search.indexing.SearchDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

class OpenSearchInfrastructureIT {

    @Test
    void indexesAndSearchesDocumentsAgainstRealOpenSearch() throws Exception {
        String indexName = "songs-infra-" + System.currentTimeMillis();
        try (RestClient restClient = RestClient.builder(HttpHost.create(openSearchUrl())).build()) {
            OpenSearchIndexClient client = new OpenSearchIndexClient(restClient, new ObjectMapper());
            SearchDocument document = new SearchDocument(
                    "infra-song-1",
                    "Night Drive",
                    "Signal Echo",
                    "Infrastructure Sessions",
                    "Electronic",
                    new BigDecimal("122.0"),
                    2026);

            client.ensureIndex(indexName);
            client.upsertAll(indexName, List.of(document));
            SearchResult result = client.search(indexName, Map.of(
                    "query", Map.of("match", Map.of("title", "Night")),
                    "from", 0,
                    "size", 10));

            assertThat(result.totalElements()).isGreaterThanOrEqualTo(1);
            assertThat(result.hits())
                    .extracting(hit -> hit.document().id())
                    .contains("infra-song-1");

            restClient.performRequest(new Request("DELETE", "/" + indexName));
        }
    }

    private static String openSearchUrl() {
        return System.getProperty(
                "infra.opensearch.url",
                System.getenv().getOrDefault("INFRA_OPENSEARCH_URL", "http://search-opensearch:9200"));
    }
}
