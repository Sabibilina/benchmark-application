package com.benchmark.search.opensearch;

import com.benchmark.search.dto.SearchRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchQueryBuilder {

    public Map<String, Object> build(SearchRequest request) {
        List<Map<String, Object>> must = new ArrayList<>();
        List<Map<String, Object>> filter = new ArrayList<>();

        if (request.q() == null) {
            must.add(Map.of("match_all", Map.of()));
        } else {
            must.add(Map.of("multi_match", Map.of(
                    "query", request.q(),
                    "fields", List.of("title^3", "artist^2", "album", "genre"))));
        }
        if (request.genre() != null) {
            filter.add(Map.of("term", Map.of("genre.keyword", request.genre())));
        }
        if (request.bpmMin() != null || request.bpmMax() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (request.bpmMin() != null) {
                range.put("gte", request.bpmMin());
            }
            if (request.bpmMax() != null) {
                range.put("lte", request.bpmMax());
            }
            filter.add(Map.of("range", Map.of("bpm", range)));
        }
        if (request.year() != null) {
            filter.add(Map.of("term", Map.of("year", request.year())));
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", must);
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", request.page() * request.size());
        body.put("size", request.size());
        body.put("query", Map.of("bool", bool));
        return body;
    }
}
