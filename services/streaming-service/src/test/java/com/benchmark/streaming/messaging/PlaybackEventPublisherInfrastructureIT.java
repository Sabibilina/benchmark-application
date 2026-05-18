package com.benchmark.streaming.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.streaming.config.StreamingProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

class PlaybackEventPublisherInfrastructureIT {

    @Test
    void publishesPlaybackEventToRealKafkaBroker() throws Exception {
        String topic = "playback-events-infra-" + System.currentTimeMillis();
        createTopic(topic);
        PlaybackEvent event = new PlaybackEvent(
                UUID.randomUUID(),
                "play.started",
                "user-1",
                "song-1",
                Instant.parse("2026-01-01T00:00:00Z"));

        KafkaTemplate<String, PlaybackEvent> kafkaTemplate = kafkaTemplate();
        try (KafkaConsumer<String, PlaybackEvent> consumer = kafkaConsumer(topic)) {
            consumer.subscribe(java.util.List.of(topic));
            new PlaybackEventPublisher(kafkaTemplate, new StreamingProperties(topic, 5, 1024)).publish(event);
            kafkaTemplate.flush();

            org.apache.kafka.clients.consumer.ConsumerRecords<String, PlaybackEvent> records =
                    consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThanOrEqualTo(1);
            assertThat(records.records(topic))
                    .anySatisfy(record -> {
                        assertThat(record.key()).isEqualTo("song-1");
                        assertThat(record.value()).isEqualTo(event);
                    });
        } finally {
            kafkaTemplate.destroy();
        }
    }

    private static void createTopic(String topic) throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers()))) {
            adminClient.createTopics(java.util.List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }
    }

    private static KafkaTemplate<String, PlaybackEvent> kafkaTemplate() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    private static KafkaConsumer<String, PlaybackEvent> kafkaConsumer(String topic) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, topic + "-consumer");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PlaybackEvent.class.getName());
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.benchmark.streaming.messaging");
        return new KafkaConsumer<>(properties);
    }

    private static String bootstrapServers() {
        return System.getProperty(
                "infra.kafka.bootstrapServers",
                System.getenv().getOrDefault("INFRA_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092"));
    }
}
