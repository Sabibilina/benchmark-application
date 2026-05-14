package com.benchmark.search.opensearch;

import java.util.List;

public record SearchResult(List<SearchHit> hits, long totalElements) {
}
