package com.benchmark.recommendation.dto;

public record RecommendationItemResponse(String songId, int rank, String reason) {
}
