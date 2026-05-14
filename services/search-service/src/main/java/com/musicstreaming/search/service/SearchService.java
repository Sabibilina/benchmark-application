package com.musicstreaming.search.service;

import com.musicstreaming.search.dto.SearchRequest;
import com.musicstreaming.search.dto.SongSearchResult;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final RestHighLevelClient client;
    private final SearchQueryBuilder queryBuilder;

    @Value("${opensearch.index-name:songs}")
    private String indexName;

    @Value("${search.max-results:20}")
    private int maxResults;

    public SearchService(RestHighLevelClient client, SearchQueryBuilder queryBuilder) {
        this.client = client;
        this.queryBuilder = queryBuilder;
    }

    public List<SongSearchResult> search(SearchRequest request) throws IOException {
        SearchSourceBuilder source = queryBuilder.build(
                request.q(), request.genre(), request.bpmMin(), request.bpmMax(), request.year(), maxResults);

        org.opensearch.action.search.SearchRequest osRequest =
                new org.opensearch.action.search.SearchRequest(indexName);
        osRequest.source(source);

        SearchResponse response = client.search(osRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = response.getHits().getHits();

        log.debug("Search returned {} hits for q={} genre={} bpmMin={} bpmMax={} year={}",
                hits.length, request.q(), request.genre(), request.bpmMin(), request.bpmMax(), request.year());

        return Arrays.stream(hits)
                .map(hit -> SongSearchResult.fromMap(hit.getSourceAsMap()))
                .toList();
    }
}
