package com.musicstreaming.search.service;

import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

@Component
public class SearchQueryBuilder {

    public SearchSourceBuilder build(String q, String genre, Double bpmMin, Double bpmMax, Integer year, int maxResults) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery();

        if (q != null && !q.isBlank()) {
            bool.must(QueryBuilders.multiMatchQuery(q, "title", "artist", "album"));
        } else {
            bool.must(QueryBuilders.matchAllQuery());
        }

        if (genre != null && !genre.isBlank()) {
            bool.filter(QueryBuilders.termQuery("genre", genre));
        }

        if (bpmMin != null || bpmMax != null) {
            RangeQueryBuilder range = QueryBuilders.rangeQuery("tempo");
            if (bpmMin != null) range.gte(bpmMin);
            if (bpmMax != null) range.lte(bpmMax);
            bool.filter(range);
        }

        if (year != null) {
            bool.filter(QueryBuilders.termQuery("year", year));
        }

        return new SearchSourceBuilder()
                .query(bool)
                .size(maxResults);
    }
}
