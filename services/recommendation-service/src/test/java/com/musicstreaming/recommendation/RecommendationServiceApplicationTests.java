package com.musicstreaming.recommendation;

import com.musicstreaming.recommendation.config.JwtTestHelper;
import com.musicstreaming.recommendation.config.RedisTestContainer;
import com.musicstreaming.recommendation.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playback-events-test"})
@Import(TestcontainersConfig.class)
class RecommendationServiceApplicationTests {

    static {
        JwtTestHelper.mintToken("bootstrap");
        RedisTestContainer.INSTANCE.isRunning();
    }

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer.INSTANCE::getHost);
        registry.add("spring.data.redis.port",
                () -> RedisTestContainer.INSTANCE.getMappedPort(6379));
    }

    @Test
    void contextLoads() {
    }
}
