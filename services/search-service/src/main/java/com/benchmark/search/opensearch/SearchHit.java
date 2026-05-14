package com.benchmark.search.opensearch;

import com.benchmark.search.indexing.SearchDocument;

public record SearchHit(SearchDocument document, double score) {
}
