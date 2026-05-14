package com.benchmark.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benchmark.recommendation.config.RecommendationProperties;
import com.benchmark.recommendation.dto.RecommendationItemResponse;
import com.benchmark.recommendation.dto.RecommendationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RecommendationCacheTest {

    @Test
    void storesAndRetrievesDailyMixRecommendations() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        RecommendationProperties properties = new RecommendationProperties("playback-events", 10, 50, Duration.ofMinutes(5));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RecommendationCache cache = new RecommendationCache(redisTemplate, objectMapper, properties);
        UUID userId = UUID.randomUUID();
        RecommendationResponse response = new RecommendationResponse("daily-mix", List.of(
                new RecommendationItemResponse("song-a", 1, "based on playback history")));

        cache.putDailyMix(userId, response);
        String key = "recommendation:daily:" + userId;
        verify(values).set(eq(key), eq(objectMapper.writeValueAsString(response)), eq(Duration.ofMinutes(5)));
        when(values.get(key)).thenReturn(objectMapper.writeValueAsString(response));

        Optional<RecommendationResponse> cached = cache.getDailyMix(userId);

        assertThat(cached).contains(response);
    }

    @Test
    void removesInvalidCachedJson() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        RecommendationCache cache = new RecommendationCache(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new RecommendationProperties("playback-events", 10, 50, Duration.ofMinutes(5)));
        UUID userId = UUID.randomUUID();
        String key = "recommendation:daily:" + userId;
        when(values.get(key)).thenReturn("{");

        assertThat(cache.getDailyMix(userId)).isEmpty();
        verify(redisTemplate).delete(key);
    }
}
