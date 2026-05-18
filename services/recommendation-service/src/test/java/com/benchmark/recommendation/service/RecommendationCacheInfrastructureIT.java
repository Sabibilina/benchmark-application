package com.benchmark.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.recommendation.config.RecommendationProperties;
import com.benchmark.recommendation.dto.RecommendationItemResponse;
import com.benchmark.recommendation.dto.RecommendationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RecommendationCacheInfrastructureIT {

    private LettuceConnectionFactory connectionFactory;

    @AfterEach
    void closeConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void storesReadsAndEvictsRecommendationsAgainstRealRedis() {
        StringRedisTemplate redisTemplate = redisTemplate();
        RecommendationCache cache = new RecommendationCache(
                redisTemplate,
                new ObjectMapper(),
                new RecommendationProperties("playback-events", 10, 50, Duration.ofMinutes(5)));
        UUID userId = UUID.randomUUID();
        RecommendationResponse response = new RecommendationResponse(
                "daily-mix",
                List.of(new RecommendationItemResponse("song-1", 1, "similar listening history")));

        cache.putDailyMix(userId, response);
        Optional<RecommendationResponse> cached = cache.getDailyMix(userId);

        assertThat(cached).contains(response);

        cache.evictDailyMix(userId);

        assertThat(cache.getDailyMix(userId)).isEmpty();
    }

    @Test
    void removesInvalidJsonCacheEntriesFromRealRedis() {
        StringRedisTemplate redisTemplate = redisTemplate();
        RecommendationCache cache = new RecommendationCache(
                redisTemplate,
                new ObjectMapper(),
                new RecommendationProperties("playback-events", 10, 50, Duration.ofMinutes(5)));
        UUID userId = UUID.randomUUID();
        String key = "recommendation:daily:" + userId;
        redisTemplate.opsForValue().set(key, "{not-json");

        assertThat(cache.getDailyMix(userId)).isEmpty();
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    private StringRedisTemplate redisTemplate() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost(), redisPort());
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private static String redisHost() {
        return System.getProperty(
                "infra.redis.host",
                System.getenv().getOrDefault("INFRA_REDIS_HOST", "recommendation-redis"));
    }

    private static int redisPort() {
        return Integer.parseInt(System.getProperty(
                "infra.redis.port",
                System.getenv().getOrDefault("INFRA_REDIS_PORT", "6379")));
    }
}
