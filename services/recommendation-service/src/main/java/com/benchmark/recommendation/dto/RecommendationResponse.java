package com.benchmark.recommendation.dto;

import java.util.List;

public record RecommendationResponse(String type, List<RecommendationItemResponse> recommendations) {
}
