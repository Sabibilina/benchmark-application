package com.musicstreaming.notification;

import com.musicstreaming.notification.config.JwtTestHelper;
import com.musicstreaming.notification.config.MongoTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playlist-events-test"})
class NotificationServiceApplicationTests {

    static {
        JwtTestHelper.mintToken("bootstrap");
        MongoTestContainer.INSTANCE.isRunning();
    }

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> MongoTestContainer.INSTANCE.getConnectionString() + "/notificationdb");
    }

    @Test
    void contextLoads() {}
}
