package com.musicstreaming.recommendation.integration;

import com.musicstreaming.recommendation.config.JwtTestHelper;
import com.musicstreaming.recommendation.config.RedisTestContainer;
import com.musicstreaming.recommendation.config.TestcontainersConfig;
import com.musicstreaming.recommendation.model.PlayEvent;
import com.musicstreaming.recommendation.model.RecommendationSong;
import com.musicstreaming.recommendation.repository.PlayEventRepository;
import com.musicstreaming.recommendation.repository.RecommendationSongRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
@Import(TestcontainersConfig.class)
class RecommendationControllerIT {

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

    @Autowired private MockMvc mvc;
    @Autowired private RecommendationSongRepository songRepo;
    @Autowired private PlayEventRepository playEventRepo;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        playEventRepo.deleteAll();
        songRepo.deleteAll();

        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private RecommendationSong insertSong(String trackId, String genre, double tempo) {
        RecommendationSong s = new RecommendationSong();
        s.setTrackId(trackId);
        s.setTitle("Title " + trackId);
        s.setArtist("Artist " + trackId);
        s.setGenre(genre);
        s.setTempo(tempo);
        s.setYear(2022);
        return songRepo.save(s);
    }

    private PlayEvent insertPlayEvent(String userId, String songId) {
        PlayEvent e = new PlayEvent();
        e.setUserId(userId);
        e.setSongId(songId);
        e.setEventType("play.started");
        e.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        return playEventRepo.save(e);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String playStartedJson(String userId, String songId) {
        return String.format(
                "{\"type\":\"play.started\",\"userId\":\"%s\",\"songId\":\"%s\",\"timestamp\":\"%s\"}",
                userId, songId, Instant.now());
    }

    // ── Auth checks ───────────────────────────────────────────────────────────

    @Test
    void dailyMix_returns401_withoutJwt() throws Exception {
        mvc.perform(get("/recommend/daily-mix"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void similar_returns401_withoutJwt() throws Exception {
        mvc.perform(get("/recommend/similar/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Daily Mix ─────────────────────────────────────────────────────────────

    @Test
    void dailyMix_returns200_withNoHistory() throws Exception {
        insertSong("t1", "pop", 120.0);
        insertSong("t2", "pop", 122.0);
        insertSong("t3", "rock", 140.0);

        String token = JwtTestHelper.mintToken("user-" + UUID.randomUUID());
        mvc.perform(get("/recommend/daily-mix").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs").isArray())
                .andExpect(jsonPath("$.songs.length()", greaterThan(0)));
    }

    @Test
    void dailyMix_returns200_withHistory() throws Exception {
        RecommendationSong song1 = insertSong("t4", "jazz", 100.0);
        RecommendationSong song2 = insertSong("t5", "jazz", 105.0);
        insertSong("t6", "jazz", 108.0);

        String userId = "user-" + UUID.randomUUID();
        insertPlayEvent(userId, String.valueOf(song1.getId()));
        insertPlayEvent(userId, String.valueOf(song2.getId()));

        String token = JwtTestHelper.mintToken(userId);
        mvc.perform(get("/recommend/daily-mix").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs").isArray())
                .andExpect(jsonPath("$.songs.length()", greaterThan(0)));
    }

    // ── Similar Songs ─────────────────────────────────────────────────────────

    @Test
    void similar_returns200_existingSong() throws Exception {
        RecommendationSong seed = insertSong("t7", "pop", 120.0);
        insertSong("t8", "pop", 125.0);
        insertSong("t9", "pop", 118.0);

        String token = JwtTestHelper.mintToken("user-" + UUID.randomUUID());
        mvc.perform(get("/recommend/similar/" + seed.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seedSongId", is(seed.getId().intValue())))
                .andExpect(jsonPath("$.songs").isArray())
                .andExpect(jsonPath("$.songs.length()", greaterThan(0)));
    }

    @Test
    void similar_returns200_unknownSong_fallbackNonEmpty() throws Exception {
        insertSong("t10", "pop", 120.0);
        insertSong("t11", "pop", 130.0);

        String token = JwtTestHelper.mintToken("user-" + UUID.randomUUID());
        mvc.perform(get("/recommend/similar/999999999").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs").isArray())
                .andExpect(jsonPath("$.songs.length()", greaterThan(0)));
    }

    @Test
    void similar_returns400_forNonNumericSongId() throws Exception {
        String token = JwtTestHelper.mintToken("user-" + UUID.randomUUID());
        mvc.perform(get("/recommend/similar/not-a-number").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    // ── Kafka consumer ────────────────────────────────────────────────────────

    @Test
    void kafkaConsumer_persistsPlayStartedEvent() throws Exception {
        String userId = "kafka-user-" + UUID.randomUUID();
        String songId = "1";

        kafkaTemplate.send("playback-events-test", songId,
                playStartedJson(userId, songId)).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(playEventRepo.count()).isGreaterThan(0));

        assertThat(playEventRepo.findAll())
                .anyMatch(e -> userId.equals(e.getUserId())
                        && songId.equals(e.getSongId())
                        && "play.started".equals(e.getEventType()));
    }

    @Test
    void kafkaConsumer_ignoresNonStartedEvents() throws Exception {
        String userId = "kafka-skip-user-" + UUID.randomUUID();
        String songId = "2";

        String skippedEvent = String.format(
                "{\"type\":\"play.skipped\",\"userId\":\"%s\",\"songId\":\"%s\",\"timestamp\":\"%s\"}",
                userId, songId, Instant.now());

        kafkaTemplate.send("playback-events-test", songId, skippedEvent).get(5, TimeUnit.SECONDS);

        // Wait briefly; play_events must remain empty for this user
        Thread.sleep(2000);
        assertThat(playEventRepo.findAll())
                .noneMatch(e -> userId.equals(e.getUserId()));
    }
}
