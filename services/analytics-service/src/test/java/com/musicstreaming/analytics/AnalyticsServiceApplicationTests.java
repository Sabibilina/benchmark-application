package com.musicstreaming.analytics;

import com.musicstreaming.analytics.config.ClickHouseTestContainer;
import com.musicstreaming.analytics.config.JwtTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playback-events-test"})
class AnalyticsServiceApplicationTests {

    static {
        JwtTestHelper.mintToken("bootstrap");
        ClickHouseTestContainer.INSTANCE.isRunning();
    }

    @DynamicPropertySource
    static void configureClickHouse(DynamicPropertyRegistry registry) {
        String host = ClickHouseTestContainer.INSTANCE.getHost();
        int port = ClickHouseTestContainer.INSTANCE.getMappedPort(8123);
        registry.add("clickhouse.jdbc-url",
                () -> "jdbc:clickhouse://" + host + ":" + port + "/default");
        registry.add("clickhouse.username", () -> "default");
        registry.add("clickhouse.password", () -> "");
    }

    @Test
    void contextLoads() {
    }
}
