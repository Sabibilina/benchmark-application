package com.musicstreaming.analytics.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.analytics.config.ClickHouseTestContainer;
import com.musicstreaming.analytics.config.JwtTestHelper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playback-events-test"})
class AnalyticsControllerIT {

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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String eventJson(String type, String userId, String songId) {
        return String.format(
                "{\"type\":\"%s\",\"userId\":\"%s\",\"songId\":\"%s\",\"timestamp\":\"%s\"}",
                type, userId, songId, Instant.now());
    }

    // ── Auth checks ── history ────────────────────────────────────────────────

    @Test
    void getHistory_returns401_withoutJWT() throws Exception {
        mvc.perform(get("/analytics/me/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHistory_returns401_withInvalidJWT() throws Exception {
        mvc.perform(get("/analytics/me/history")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ── Auth checks ── global charts ──────────────────────────────────────────

    @Test
    void getGlobalCharts_returns401_withoutJWT() throws Exception {
        mvc.perform(get("/analytics/charts/global"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGlobalCharts_returns401_withInvalidJWT() throws Exception {
        mvc.perform(get("/analytics/charts/global")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    void getHistory_returns200_emptyArray_forNewUser() throws Exception {
        String token = JwtTestHelper.mintToken("new-user-" + UUID.randomUUID());
        mvc.perform(get("/analytics/me/history")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void getGlobalCharts_returns200_withValidJWT() throws Exception {
        String token = JwtTestHelper.mintToken("chart-reader-" + UUID.randomUUID());
        mvc.perform(get("/analytics/charts/global")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── Kafka consumer integration ─────────────────────────────────────────────

    @Test
    void getHistory_returnsEntry_afterKafkaPlaybackEvent() throws Exception {
        String userId = "hist-user-" + UUID.randomUUID();
        String songId = "hist-song-" + UUID.randomUUID();
        String token = JwtTestHelper.mintToken(userId);

        kafkaTemplate.send("playback-events-test", songId,
                eventJson("play.started", userId, songId)).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/analytics/me/history")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()", greaterThan(0)))
                        .andExpect(jsonPath("$[0].songId", is(songId)))
                        .andExpect(jsonPath("$[0].eventType", is("play.started")))
                        .andExpect(jsonPath("$[0].timestamp", notNullValue()))
        );
    }

    @Test
    void getHistory_allEventTypesRecorded() throws Exception {
        String userId = "multi-type-user-" + UUID.randomUUID();
        String song = "multi-type-song-" + UUID.randomUUID();
        String token = JwtTestHelper.mintToken(userId);

        kafkaTemplate.send("playback-events-test", song,
                eventJson("play.started", userId, song)).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("playback-events-test", song,
                eventJson("play.ended", userId, song)).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/analytics/me/history")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
        );
    }

    @Test
    void getHistory_isolatesEventsByUser() throws Exception {
        String user1 = "isolated-user1-" + UUID.randomUUID();
        String user2 = "isolated-user2-" + UUID.randomUUID();
        String song1 = "isolated-song-" + UUID.randomUUID();
        String token1 = JwtTestHelper.mintToken(user1);
        String token2 = JwtTestHelper.mintToken(user2);

        kafkaTemplate.send("playback-events-test", song1,
                eventJson("play.started", user1, song1)).get(5, TimeUnit.SECONDS);

        // Wait for user1's event to be stored
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/analytics/me/history")
                                .header("Authorization", bearer(token1)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.songId == '" + song1 + "')]").isNotEmpty())
        );

        // user2 must not see user1's event
        mvc.perform(get("/analytics/me/history")
                        .header("Authorization", bearer(token2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.songId == '" + song1 + "')]").isEmpty());
    }

    // ── Global charts ranking ─────────────────────────────────────────────────

    @Test
    void getGlobalCharts_returnsHigherRankForMorePlayedSong() throws Exception {
        String topSong = "top-song-" + UUID.randomUUID();
        String lowSong = "low-song-" + UUID.randomUUID();
        String userId = "chart-user-" + UUID.randomUUID();
        String token = JwtTestHelper.mintToken(userId);

        for (int i = 0; i < 3; i++) {
            kafkaTemplate.send("playback-events-test", topSong,
                    eventJson("play.started", userId, topSong)).get(5, TimeUnit.SECONDS);
        }
        kafkaTemplate.send("playback-events-test", lowSong,
                eventJson("play.started", userId, lowSong)).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String body = mvc.perform(get("/analytics/charts/global")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andReturn().getResponse().getContentAsString();

            List<?> charts = objectMapper.readValue(body, List.class);
            int topIdx = -1, lowIdx = -1;
            for (int i = 0; i < charts.size(); i++) {
                Map<?, ?> entry = (Map<?, ?>) charts.get(i);
                if (topSong.equals(entry.get("songId"))) topIdx = i;
                if (lowSong.equals(entry.get("songId"))) lowIdx = i;
            }
            assertThat(topIdx).as("top song must appear in global charts").isGreaterThanOrEqualTo(0);
            assertThat(lowIdx).as("low song must appear in global charts").isGreaterThanOrEqualTo(0);
            assertThat(topIdx).as("top song must rank higher (lower index) than low song").isLessThan(lowIdx);
        });
    }

    @Test
    void getGlobalCharts_doesNotCountSkippedEventsAsPlays() throws Exception {
        String skippedSong = "skipped-song-" + UUID.randomUUID();
        String userId = "skip-user-" + UUID.randomUUID();
        String token = JwtTestHelper.mintToken(userId);

        // Only play.skipped events — should not appear in charts
        kafkaTemplate.send("playback-events-test", skippedSong,
                eventJson("play.skipped", userId, skippedSong)).get(5, TimeUnit.SECONDS);

        // Give consumer time to process; song must not appear in charts
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/analytics/charts/global")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.songId == '" + skippedSong + "')]").isEmpty())
        );
    }

    @Test
    void getGlobalCharts_responseHasExpectedFields() throws Exception {
        String songId = "field-check-song-" + UUID.randomUUID();
        String userId = "field-check-user-" + UUID.randomUUID();
        String token = JwtTestHelper.mintToken(userId);

        kafkaTemplate.send("playback-events-test", songId,
                eventJson("play.started", userId, songId)).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/analytics/charts/global")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.songId == '" + songId + "')].rank", not(empty())))
                        .andExpect(jsonPath("$[?(@.songId == '" + songId + "')].playCount", not(empty())))
        );
    }
}
