package com.musicstreaming.playlist.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.playlist.config.JwtTestHelper;
import com.musicstreaming.playlist.config.TestcontainersConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"playlist-events-test"})
class PlaylistControllerIT {

    static {
        JwtTestHelper.mintToken("bootstrap");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${playlist.kafka.topic}")
    private String kafkaTopic;

    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        aliceToken = JwtTestHelper.mintToken(ALICE);
        bobToken   = JwtTestHelper.mintToken(BOB);
        jdbc.execute("DELETE FROM playlist_tracks");
        jdbc.execute("DELETE FROM playlists");
    }

    // ── 401 without token ────────────────────────────────────────────────────

    @Test
    void allEndpoints_return401WithoutToken() throws Exception {
        mvc.perform(get("/playlists")).andExpect(status().isUnauthorized());
        mvc.perform(post("/playlists").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}")).andExpect(status().isUnauthorized());
        UUID id = UUID.randomUUID();
        mvc.perform(get("/playlists/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/playlists/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/playlists/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/playlists/" + id + "/tracks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"songId\":\"s\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/playlists/" + id + "/tracks/s")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/playlists/" + id + "/tracks/reorder").contentType(MediaType.APPLICATION_JSON)
                .content("{\"songIds\":[\"s\"]}")).andExpect(status().isUnauthorized());
    }

    @Test
    void allEndpoints_return401WithInvalidToken() throws Exception {
        mvc.perform(get("/playlists").header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /playlists ────────────────────────────────────────────────────────

    @Test
    void getPlaylists_autoCreatesLikedSongs() throws Exception {
        MvcResult result = mvc.perform(get("/playlists").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(arr.isArray()).isTrue();
        boolean hasLikedSongs = false;
        for (JsonNode node : arr) {
            if (node.get("likedSongs").asBoolean()) hasLikedSongs = true;
        }
        assertThat(hasLikedSongs).isTrue();
    }

    @Test
    void getPlaylists_returnsOnlyOwnPlaylists() throws Exception {
        createPlaylist(aliceToken, "Alice Mix");

        MvcResult result = mvc.perform(get("/playlists").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode node : arr) {
            assertThat(node.get("name").asText()).isNotEqualTo("Alice Mix");
        }
    }

    // ── POST /playlists ───────────────────────────────────────────────────────

    @Test
    void createPlaylist_returns201() throws Exception {
        MvcResult result = mvc.perform(post("/playlists")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Workout Mix\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("name").asText()).isEqualTo("Workout Mix");
        assertThat(body.get("likedSongs").asBoolean()).isFalse();
    }

    @Test
    void createPlaylist_rejectsLikedSongsName() throws Exception {
        mvc.perform(post("/playlists")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Liked Songs\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPlaylist_rejectsDuplicateName() throws Exception {
        createPlaylist(aliceToken, "My Playlist");

        mvc.perform(post("/playlists")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Playlist\"}"))
                .andExpect(status().isConflict());
    }

    // ── GET /playlists/:id ────────────────────────────────────────────────────

    @Test
    void getPlaylist_returns200ForOwner() throws Exception {
        UUID id = createPlaylist(aliceToken, "My Playlist");

        mvc.perform(get("/playlists/" + id).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Playlist"));
    }

    @Test
    void getPlaylist_returns404ForOtherUser() throws Exception {
        UUID id = createPlaylist(aliceToken, "Private");

        mvc.perform(get("/playlists/" + id).header("Authorization", bearer(bobToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPlaylist_returns404ForUnknownId() throws Exception {
        mvc.perform(get("/playlists/" + UUID.randomUUID()).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /playlists/:id ──────────────────────────────────────────────────

    @Test
    void updatePlaylist_updatesName() throws Exception {
        UUID id = createPlaylist(aliceToken, "Old Name");

        mvc.perform(patch("/playlists/" + id)
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updatePlaylist_rejectsLikedSongsRename() throws Exception {
        MvcResult listResult = mvc.perform(get("/playlists").header("Authorization", bearer(aliceToken))).andReturn();
        JsonNode arr = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID likedId = null;
        for (JsonNode node : arr) {
            if (node.get("likedSongs").asBoolean()) {
                likedId = UUID.fromString(node.get("id").asText());
            }
        }

        mvc.perform(patch("/playlists/" + likedId)
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /playlists/:id ─────────────────────────────────────────────────

    @Test
    void deletePlaylist_returns204() throws Exception {
        UUID id = createPlaylist(aliceToken, "Temp");

        mvc.perform(delete("/playlists/" + id).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/playlists/" + id).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePlaylist_returns400ForLikedSongs() throws Exception {
        MvcResult listResult = mvc.perform(get("/playlists").header("Authorization", bearer(aliceToken))).andReturn();
        JsonNode arr = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID likedId = null;
        for (JsonNode node : arr) {
            if (node.get("likedSongs").asBoolean()) likedId = UUID.fromString(node.get("id").asText());
        }

        mvc.perform(delete("/playlists/" + likedId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /playlists/:id/tracks ────────────────────────────────────────────

    @Test
    void addTrack_returns201AndTrackPresent() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");

        MvcResult result = mvc.perform(post("/playlists/" + playlistId + "/tracks")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"song-abc\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode tracks = body.get("tracks");
        assertThat(tracks.isArray()).isTrue();
        assertThat(tracks.size()).isEqualTo(1);
        assertThat(tracks.get(0).get("songId").asText()).isEqualTo("song-abc");
        assertThat(tracks.get(0).get("position").asInt()).isEqualTo(0);
    }

    @Test
    void addTrack_returns409OnDuplicate() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");
        addTrack(aliceToken, playlistId, "song-dup");

        mvc.perform(post("/playlists/" + playlistId + "/tracks")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"song-dup\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addTrack_returns404ForOtherUsersPlaylist() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Alice's Mix");

        mvc.perform(post("/playlists/" + playlistId + "/tracks")
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"song-x\"}"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /playlists/:id/tracks/:songId ──────────────────────────────────

    @Test
    void removeTrack_returns204() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");
        addTrack(aliceToken, playlistId, "song-1");

        mvc.perform(delete("/playlists/" + playlistId + "/tracks/song-1")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());

        MvcResult result = mvc.perform(get("/playlists/" + playlistId)
                .header("Authorization", bearer(aliceToken))).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("tracks").size()).isEqualTo(0);
    }

    @Test
    void removeTrack_returns404ForMissingSong() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");

        mvc.perform(delete("/playlists/" + playlistId + "/tracks/nonexistent-song")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /playlists/:id/tracks/reorder ───────────────────────────────────

    @Test
    void reorderTracks_updatesPositions() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");
        addTrack(aliceToken, playlistId, "song-A");
        addTrack(aliceToken, playlistId, "song-B");
        addTrack(aliceToken, playlistId, "song-C");

        MvcResult result = mvc.perform(patch("/playlists/" + playlistId + "/tracks/reorder")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songIds\":[\"song-C\",\"song-A\",\"song-B\"]}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tracks = objectMapper.readTree(result.getResponse().getContentAsString()).get("tracks");
        assertThat(tracks.get(0).get("songId").asText()).isEqualTo("song-C");
        assertThat(tracks.get(1).get("songId").asText()).isEqualTo("song-A");
        assertThat(tracks.get(2).get("songId").asText()).isEqualTo("song-B");
    }

    @Test
    void reorderTracks_returns400OnMismatch() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");
        addTrack(aliceToken, playlistId, "song-A");

        mvc.perform(patch("/playlists/" + playlistId + "/tracks/reorder")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songIds\":[\"wrong-id\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ── Kafka event verification ───────────────────────────────────────────────

    @Test
    void addTrack_publishesKafkaEvent() throws Exception {
        UUID playlistId = createPlaylist(aliceToken, "Mix");

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-consumer-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", StringDeserializer.class);

        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, kafkaTopic);

        addTrack(aliceToken, playlistId, "song-kafka");

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        boolean foundTrackAdded = false;
        for (var record : records) {
            if (record.value().contains("TRACK_ADDED")) {
                foundTrackAdded = true;
                break;
            }
        }
        assertThat(foundTrackAdded).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private UUID createPlaylist(String token, String name) throws Exception {
        MvcResult result = mvc.perform(post("/playlists")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private void addTrack(String token, UUID playlistId, String songId) throws Exception {
        mvc.perform(post("/playlists/" + playlistId + "/tracks")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"" + songId + "\"}"))
                .andExpect(status().isCreated());
    }
}
