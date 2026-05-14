package com.benchmark.notification.messaging;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.benchmark.notification.service.NotificationService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
        "app.notification.playlist-events-topic=notification-playlist-events-test",
        "spring.kafka.consumer.group-id=notification-service-test",
        "spring.data.mongodb.uri=mongodb://localhost:1/notification-test",
        "management.health.mongo.enabled=false"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "notification-playlist-events-test",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PlaylistUpdateKafkaIntegrationTest {

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockBean
    NotificationService notificationService;

    @Test
    void malformedRecordDoesNotBlockLaterValidPlaylistUpdateEvent() {
        String topic = "notification-playlist-events-test";
        sendRaw(topic, "not valid json".getBytes(), false);
        UUID eventId = UUID.randomUUID();
        String validJson = """
                {
                  "eventId": "%s",
                  "eventType": "playlist.updated",
                  "actorUserId": "actor-user",
                  "recipientUserIds": ["recipient-user"],
                  "playlistId": "playlist-1",
                  "playlistName": "Road Trip",
                  "occurredAt": "2026-01-01T00:00:00Z",
                  "metadata": {"change": "track-added"}
                }
                """.formatted(eventId);
        sendRaw(topic, validJson.getBytes(), true);

        verify(notificationService, timeout(10000)).processPlaylistUpdate(argThat(event ->
                event != null
                        && event.eventId().equals(eventId.toString())
                        && event.eventType().equals("playlist.updated")
                        && event.recipientUserIds().contains("recipient-user")
                        && event.occurredAt().equals(Instant.parse("2026-01-01T00:00:00Z"))));
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
                        "com.benchmark.playlist.messaging.PlaylistUpdateEvent".getBytes()));
            }
            producer.send(record).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send test playlist update event", exception);
        }
    }
}
