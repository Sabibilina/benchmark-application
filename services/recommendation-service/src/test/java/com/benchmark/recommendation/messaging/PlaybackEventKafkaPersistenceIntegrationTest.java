package com.benchmark.recommendation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.benchmark.recommendation.TestKeyFiles;
import com.benchmark.recommendation.repository.UserSongInteractionRepository;
import com.benchmark.recommendation.service.RecommendationCache;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "app.recommendation.playback-events-topic=recommendation-playback-events-persistence-test",
        "spring.kafka.consumer.group-id=recommendation-service-persistence-test",
        "spring.datasource.url=jdbc:h2:mem:recommendation-kafka-persistence;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.health.redis.enabled=false",
        "management.health.db.enabled=false"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "recommendation-playback-events-persistence-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PlaybackEventKafkaPersistenceIntegrationTest {

    @TempDir
    static Path keyDir;

    static Path publicKey;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    UserSongInteractionRepository interactionRepository;

    @MockBean
    RecommendationCache recommendationCache;

    @BeforeAll
    static void createKeys() {
        publicKey = TestKeyFiles.writePublicKey(keyDir);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> publicKey.toString());
    }

    @Test
    void validPlaybackEventIsConsumedAndPersisted() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String event = """
                {
                  "eventId": "%s",
                  "type": "play.started",
                  "userId": "%s",
                  "songId": "song-a",
                  "timestamp": "2026-01-01T00:00:00Z"
                }
                """.formatted(eventId, userId);

        send("recommendation-playback-events-persistence-test", event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(interactionRepository.existsByEventId(eventId)).isTrue();
            assertThat(interactionRepository.findTopPositiveSongsForUser(userId.toString(), 5))
                    .containsExactly("song-a");
        });
    }

    private void send(String topic, String value) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, null, value)).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send test playback event", exception);
        }
    }
}
