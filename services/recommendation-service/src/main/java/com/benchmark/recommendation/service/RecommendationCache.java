package com.benchmark.recommendation.service;

import com.benchmark.recommendation.config.RecommendationProperties;
import com.benchmark.recommendation.dto.RecommendationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RecommendationCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;

    public RecommendationCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RecommendationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.properties = properties;
    }

    public Optional<RecommendationResponse> getDailyMix(UUID userId) {
        return get(key("daily", userId.toString()));
    }

    public void putDailyMix(UUID userId, RecommendationResponse response) {
        put(key("daily", userId.toString()), response);
    }

    public void evictDailyMix(UUID userId) {
        redisTemplate.delete(key("daily", userId.toString()));
    }

    public Optional<RecommendationResponse> getSimilar(String songId) {
        return get(key("similar", songId));
    }

    public void putSimilar(String songId, RecommendationResponse response) {
        put(key("similar", songId), response);
    }

    public void evictSimilar(String songId) {
        redisTemplate.delete(key("similar", songId));
    }

    private Optional<RecommendationResponse> get(String key) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, RecommendationResponse.class));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Removing invalid recommendation cache entry for key={}", key, exception);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private void put(String key, RecommendationResponse response) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), properties.cacheTtl());
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to serialize recommendation cache entry for key={}", key, exception);
        }
    }

    private static String key(String type, String id) {
        return "recommendation:" + type + ":" + id;
    }
}
