package com.musicstreaming.notification.integration;

import com.musicstreaming.notification.config.JwtTestHelper;
import com.musicstreaming.notification.config.MongoTestContainer;
import com.musicstreaming.notification.model.Notification;
import com.musicstreaming.notification.repository.NotificationRepository;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playlist-events-test"})
class NotificationControllerIT {

    static {
        JwtTestHelper.mintToken("bootstrap");
        MongoTestContainer.INSTANCE.isRunning();
    }

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> MongoTestContainer.INSTANCE.getConnectionString() + "/notificationdb");
    }

    @Autowired private MockMvc mvc;
    @Autowired private NotificationRepository repository;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // ── Auth checks ──────────────────────────────────────────────────────────

    @Test
    void getNotifications_noJwt_returns401() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_validJwt_returnsEmpty_whenNoNotifications() throws Exception {
        String token = JwtTestHelper.mintToken("user-empty");
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // ── Kafka consumer ────────────────────────────────────────────────────────

    @Test
    void kafkaConsumer_playlistCreated_storesNotification() throws Exception {
        String userId = "user-" + UUID.randomUUID();
        String playlistId = UUID.randomUUID().toString();

        kafkaTemplate.send("playlist-events-test", playlistId,
                eventJson("PLAYLIST_CREATED", playlistId, userId, "My Playlist"))
                .get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findAll())
                        .anyMatch(n -> userId.equals(n.getUserId())
                                && "PLAYLIST_CREATED".equals(n.getType())));

        String token = JwtTestHelper.mintToken(userId);
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[0].type", is("PLAYLIST_CREATED")));
    }

    @Test
    void kafkaConsumer_trackAdded_storesNotification() throws Exception {
        String userId = "user-" + UUID.randomUUID();
        String playlistId = UUID.randomUUID().toString();

        kafkaTemplate.send("playlist-events-test", playlistId,
                eventJson("TRACK_ADDED", playlistId, userId, "Liked Songs"))
                .get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findAll())
                        .anyMatch(n -> userId.equals(n.getUserId())
                                && "TRACK_ADDED".equals(n.getType())));
    }

    @Test
    void kafkaConsumer_notificationsFiltered_byUserId() throws Exception {
        String userA = "userA-" + UUID.randomUUID();
        String userB = "userB-" + UUID.randomUUID();
        String playlistId = UUID.randomUUID().toString();

        kafkaTemplate.send("playlist-events-test", playlistId,
                eventJson("PLAYLIST_CREATED", playlistId, userA, "Playlist A"))
                .get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findAll()).anyMatch(n -> userA.equals(n.getUserId())));

        String tokenB = JwtTestHelper.mintToken(userB);
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void getNotifications_returnsNewestFirst() throws Exception {
        String userId = "user-" + UUID.randomUUID();

        Notification older = buildNotification(userId, "TRACK_ADDED", "ref1",
                Instant.now().minusSeconds(60));
        Notification newer = buildNotification(userId, "PLAYLIST_CREATED", "ref2",
                Instant.now());
        repository.saveAll(List.of(older, newer));

        String token = JwtTestHelper.mintToken(userId);
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].type", is("PLAYLIST_CREATED")))
                .andExpect(jsonPath("$[1].type", is("TRACK_ADDED")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String eventJson(String eventType, String playlistId, String userId, String playlistName) {
        return String.format(
                "{\"eventType\":\"%s\",\"playlistId\":\"%s\",\"userId\":\"%s\","
                        + "\"playlistName\":\"%s\",\"timestamp\":\"%s\"}",
                eventType, playlistId, userId, playlistName, Instant.now());
    }

    private Notification buildNotification(String userId, String type, String referenceId, Instant createdAt) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle("Title");
        n.setMessage("Message");
        n.setReferenceId(referenceId);
        n.setRead(false);
        n.setCreatedAt(createdAt);
        return n;
    }
}
