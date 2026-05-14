package com.benchmark.search.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.dto.SearchRequest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchQueryBuilderTest {

    private final SearchProperties properties = new SearchProperties(
            "http://localhost:9200", "songs", Path.of("catalog.csv"), true, 500, 20, 100);
    private final OpenSearchQueryBuilder builder = new OpenSearchQueryBuilder();

    @Test
    void buildsTextSearchQuery() {
        Map<String, Object> query = builder.build(request("ocean", null, null, null, null));

        assertThat(query.toString()).contains("multi_match", "ocean", "title^3", "artist^2");
    }

    @Test
    void buildsGenreFilter() {
        Map<String, Object> query = builder.build(request(null, "dance", null, null, null));

        assertThat(query.toString()).contains("genre.keyword", "dance");
    }

    @Test
    void buildsBpmRangeFilter() {
        Map<String, Object> query = builder.build(request(null, null, new BigDecimal("90"), new BigDecimal("120"), null));

        assertThat(query.toString()).contains("bpm", "gte=90", "lte=120");
    }

    @Test
    void buildsYearFilterAndCombinedFilters() {
        Map<String, Object> query = builder.build(request("quiet", "indie", new BigDecimal("80"), new BigDecimal("100"), 2020));

        assertThat(query.toString()).contains("quiet", "genre.keyword", "indie", "bpm", "year=2020");
    }

    private SearchRequest request(String q, String genre, BigDecimal bpmMin, BigDecimal bpmMax, Integer year) {
        return SearchRequest.of(q, genre, bpmMin, bpmMax, year, 0, 20, properties);
    }
}
