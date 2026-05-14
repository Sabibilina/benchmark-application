package com.benchmark.analytics.messaging;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import com.benchmark.analytics.persistence.AnalyticsSchemaInitializer;
import com.benchmark.analytics.support.TestKeyFiles;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = PlaybackEventKafkaRobustnessIntegrationTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PlaybackEventKafkaRobustnessIntegrationTest {

    static final String TOPIC = "playback-events-robustness-test";
    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();
    private static final String CANONICAL_USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    @MockBean
    AnalyticsEventRepository repository;

    @MockBean
    AnalyticsSchemaInitializer schemaInitializer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("app.analytics.playback-events-topic", () -> TOPIC);
        registry.add("spring.kafka.consumer.group-id", () -> "analytics-robustness-test");
        registry.add("management.health.db.enabled", () -> "false");
    }

    @Test
    void malformedRecordDoesNotBlockLaterValidPlaybackEvent() {
        listenerRegistry.getListenerContainers()
                .forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));

        try (Producer<String, byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(TOPIC, "bad", "not-json".getBytes(StandardCharsets.UTF_8)));

            String eventId = UUID.randomUUID().toString();
            String validJson = """
                    {"eventId":"%s","type":"play.started","userId":"%s","songId":"song-1","timestamp":"%s"}
                    """.formatted(eventId, CANONICAL_USER_ID, Instant.parse("2026-05-14T10:00:00Z"));
            ProducerRecord<String, byte[]> validRecord = new ProducerRecord<>(
                    TOPIC,
                    "good",
                    validJson.getBytes(StandardCharsets.UTF_8));
            validRecord.headers().add(new RecordHeader(
                    "__TypeId__",
                    "com.benchmark.streaming.messaging.PlaybackEvent".getBytes(StandardCharsets.UTF_8)));

            producer.send(validRecord);
            producer.flush();

            verify(repository, timeout(10000)).save(argThat(record ->
                    record.eventId().toString().equals(eventId)
                            && record.eventType().equals("play.started")
                            && record.userId().equals(CANONICAL_USER_ID)
                            && record.songId().equals("song-1")));
        }
    }

    private Producer<String, byte[]> producer() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        return new DefaultKafkaProducerFactory<>(
                producerProps,
                new StringSerializer(),
                new ByteArraySerializer()).createProducer();
    }
}
