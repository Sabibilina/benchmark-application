package com.musicstreaming.streaming.integration;

import com.musicstreaming.streaming.config.JwtTestHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playback-events-test"})
class StreamingControllerIT {

    static {
        JwtTestHelper.mintToken("bootstrap");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private String token;

    @BeforeEach
    void setUp() {
        token = JwtTestHelper.mintToken("testuser");
    }

    private String bearer() {
        return "Bearer " + token;
    }

    // ── 401 without token ─────────────────────────────────────────────────────

    @Test
    void getStream_withoutToken_returns401() throws Exception {
        mvc.perform(get("/stream/song-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStream_withInvalidToken_returns401() throws Exception {
        mvc.perform(get("/stream/song-1")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSegment_withoutToken_returns401() throws Exception {
        mvc.perform(get("/stream/song-1/segment/0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completeStream_withoutToken_returns401() throws Exception {
        mvc.perform(post("/stream/song-1/complete"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void skipStream_withoutToken_returns401() throws Exception {
        mvc.perform(post("/stream/song-1/skip"))
                .andExpect(status().isUnauthorized());
    }

    // ── Manifest endpoint ─────────────────────────────────────────────────────

    @Test
    void getStream_withValidToken_returns200() throws Exception {
        mvc.perform(get("/stream/song-1")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void getStream_withValidToken_contentTypeIsM3U8() throws Exception {
        mvc.perform(get("/stream/song-1")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("application/vnd.apple.mpegurl")));
    }

    @Test
    void getStream_withValidToken_bodyContainsM3U8Structure() throws Exception {
        String body = mvc.perform(get("/stream/song-abc")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("#EXTM3U");
        assertThat(body).contains("/stream/song-abc/segment/");
        assertThat(body).contains("#EXT-X-ENDLIST");
        // test profile sets count=3
        long segmentLines = body.lines().filter(l -> l.startsWith("/stream/")).count();
        assertThat(segmentLines).isEqualTo(3);
    }

    @Test
    void getStream_withValidToken_publishesPlayStartedEvent() throws Exception {
        String songId = "song-started-" + UUID.randomUUID();
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-started-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "playback-events-test");

        mvc.perform(get("/stream/" + songId).header("Authorization", bearer()))
                .andExpect(status().isOk());

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        boolean found = false;
        for (var record : records) {
            if (record.value().contains("play.started") && record.value().contains(songId)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Expected play.started event for song %s in Kafka", songId).isTrue();
    }

    // ── Segment endpoint ──────────────────────────────────────────────────────

    @Test
    void getSegment_withValidToken_returns200() throws Exception {
        mvc.perform(get("/stream/song-1/segment/0")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void getSegment_withValidToken_returnsCorrectByteLength() throws Exception {
        byte[] body = mvc.perform(get("/stream/song-1/segment/0")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // test profile sets size-bytes=1024
        assertThat(body).hasSize(1024);
    }

    // ── Complete endpoint ─────────────────────────────────────────────────────

    @Test
    void completeStream_withValidToken_returns204() throws Exception {
        mvc.perform(post("/stream/song-1/complete")
                        .header("Authorization", bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void completeStream_withValidToken_publishesPlayEndedEvent() throws Exception {
        String songId = "song-ended-" + UUID.randomUUID();
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-ended-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "playback-events-test");

        mvc.perform(post("/stream/" + songId + "/complete").header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        boolean found = false;
        for (var record : records) {
            if (record.value().contains("play.ended") && record.value().contains(songId)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Expected play.ended event for song %s in Kafka", songId).isTrue();
    }

    // ── Skip endpoint ─────────────────────────────────────────────────────────

    @Test
    void skipStream_withValidToken_returns204() throws Exception {
        mvc.perform(post("/stream/song-1/skip")
                        .header("Authorization", bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void skipStream_withValidToken_publishesPlaySkippedEvent() throws Exception {
        String songId = "song-skipped-" + UUID.randomUUID();
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-skipped-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "playback-events-test");

        mvc.perform(post("/stream/" + songId + "/skip").header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        boolean found = false;
        for (var record : records) {
            if (record.value().contains("play.skipped") && record.value().contains(songId)) {
                found = true;
                break;
            }
        }
        assertThat(found).as("Expected play.skipped event for song %s in Kafka", songId).isTrue();
    }
}
