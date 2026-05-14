package com.benchmark.search.service;

import com.benchmark.search.config.SearchProperties;
import com.benchmark.search.dto.SearchPageResponse;
import com.benchmark.search.dto.SearchRequest;
import com.benchmark.search.dto.SearchResultResponse;
import com.benchmark.search.opensearch.OpenSearchIndexClient;
import com.benchmark.search.opensearch.OpenSearchQueryBuilder;
import com.benchmark.search.opensearch.SearchHit;
import com.benchmark.search.opensearch.SearchResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final SearchProperties properties;
    private final OpenSearchIndexClient indexClient;
    private final OpenSearchQueryBuilder queryBuilder;

    public SearchService(SearchProperties properties, OpenSearchIndexClient indexClient, OpenSearchQueryBuilder queryBuilder) {
        this.properties = properties;
        this.indexClient = indexClient;
        this.queryBuilder = queryBuilder;
    }

    public SearchPageResponse search(SearchRequest request) {
        Map<String, Object> query = queryBuilder.build(request);
        SearchResult result = indexClient.search(properties.indexName(), query);
        List<SearchResultResponse> content = result.hits().stream()
                .map(this::toResponse)
                .toList();
        return new SearchPageResponse(content, result.totalElements(), request.page(), request.size());
    }

    private SearchResultResponse toResponse(SearchHit hit) {
        return new SearchResultResponse(
                hit.document().id(),
                hit.document().title(),
                hit.document().artist(),
                hit.document().album(),
                hit.document().genre(),
                hit.document().bpm(),
                hit.document().year(),
                hit.score());
    }
}
