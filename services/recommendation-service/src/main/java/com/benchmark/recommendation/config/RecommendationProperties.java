package com.benchmark.recommendation.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        @NotBlank String playbackEventsTopic,
        @Min(1) @Max(100) int defaultLimit,
        @Min(1) @Max(100) int maxLimit,
        @NotNull Duration cacheTtl) {
}
