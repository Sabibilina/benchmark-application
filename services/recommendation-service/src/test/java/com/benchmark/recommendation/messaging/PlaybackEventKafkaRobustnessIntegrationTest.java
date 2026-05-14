package com.benchmark.recommendation.messaging;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.benchmark.recommendation.TestKeyFiles;
import com.benchmark.recommendation.service.RecommendationService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
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
        "app.recommendation.playback-events-topic=recommendation-playback-events-test",
        "spring.kafka.consumer.group-id=recommendation-service-test",
        "spring.datasource.url=jdbc:h2:mem:recommendation-kafka;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "management.health.redis.enabled=false",
        "management.health.db.enabled=false"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "recommendation-playback-events-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PlaybackEventKafkaRobustnessIntegrationTest {

    @TempDir
    static Path keyDir;

    static Path publicKey;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockBean
    RecommendationService recommendationService;

    @BeforeAll
    static void createKeys() {
        publicKey = TestKeyFiles.writePublicKey(keyDir);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> publicKey.toString());
    }

    @Test
    void incompatibleRecordDoesNotBlockLaterValidPlaybackEvent() {
        String topic = "recommendation-playback-events-test";
        sendRaw(topic, "not valid json".getBytes());
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String validJson = """
                {
                  "eventId": "%s",
                  "type": "play.started",
                  "userId": "%s",
                  "songId": "song-a",
                  "timestamp": "2026-01-01T00:00:00Z"
                }
                """.formatted(eventId, userId);
        sendRawWithLegacyTypeHeader(topic, validJson.getBytes());

        verify(recommendationService, timeout(10000)).recordPlaybackEvent(argThat(event ->
                event != null
                        && event.eventId().equals(eventId.toString())
                        && event.type().equals("play.started")
                        && event.userId().equals(userId.toString())
                        && event.songId().equals("song-a")
                        && event.timestamp().equals(Instant.parse("2026-01-01T00:00:00Z"))));
    }

    private void sendRaw(String topic, byte[] value) {
        sendRaw(topic, value, false);
    }

    private void sendRawWithLegacyTypeHeader(String topic, byte[] value) {
        sendRaw(topic, value, true);
    }

    private void sendRaw(String topic, byte[] value, boolean legacyTypeHeader) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(properties)) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, null, value);
            if (legacyTypeHeader) {
                record.headers().add(new RecordHeader(
                        "__TypeId__",
                        "com.benchmark.streaming.messaging.PlaybackEvent".getBytes()));
            }
            producer.send(record).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send test playback event", exception);
        }
    }
}
