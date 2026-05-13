package com.benchmark.playlist.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.playlist.repository.PlaylistRepository;
import com.benchmark.playlist.support.TestKeyFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlaylistControllerIntegrationTest {

    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PlaylistRepository playlistRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:playlist;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("app.playlist.liked-songs-name", () -> "Liked Songs");
    }

    @Test
    void playlistEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/playlists")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/playlists").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/playlists/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/playlists/00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/playlists/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/playlists/00000000-0000-0000-0000-000000000001/tracks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/playlists/00000000-0000-0000-0000-000000000001/tracks/song-1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/playlists/00000000-0000-0000-0000-000000000001/tracks/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listCreatesLikedSongsAndReturnsOnlyCurrentUsersPlaylists() throws Exception {
        createPlaylist("playlist-owner-a", "Private Mix", null);
        createPlaylist("playlist-owner-b", "Other Mix", null);

        mockMvc.perform(get("/playlists").with(jwt().jwt(jwt -> jwt.subject("playlist-owner-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Liked Songs"))
                .andExpect(jsonPath("$[0].likedSongs").value(true))
                .andExpect(jsonPath("$[1].name").value("Private Mix"));
    }

    @Test
    void playlistCrudTrackManagementAndReorderPersistForOwner() throws Exception {
        String owner = "playlist-flow-owner";
        String playlistId = createPlaylist(owner, "Road Trip", "Initial description")
                .get("id")
                .asText();

        mockMvc.perform(get("/playlists/{id}", playlistId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Road Trip"))
                .andExpect(jsonPath("$.tracks", hasSize(0)));

        mockMvc.perform(patch("/playlists/{id}", playlistId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Road Trip","description":"Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Road Trip"));

        mockMvc.perform(post("/playlists/{id}/tracks", playlistId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"songId":"song-a"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.songId").value("song-a"))
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.id", notNullValue()));

        mockMvc.perform(post("/playlists/{id}/tracks", playlistId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"songId":"song-b"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(patch("/playlists/{id}/tracks/reorder", playlistId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"songIds":["song-b","song-a"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].songId").value("song-b"))
                .andExpect(jsonPath("$.tracks[1].songId").value("song-a"));

        mockMvc.perform(delete("/playlists/{id}/tracks/song-b", playlistId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/playlists/{id}", playlistId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks", hasSize(1)))
                .andExpect(jsonPath("$.tracks[0].songId").value("song-a"))
                .andExpect(jsonPath("$.tracks[0].position").value(0));

        mockMvc.perform(delete("/playlists/{id}", playlistId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/playlists/{id}", playlistId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void likedSongsCannotBeDeletedOrRenamed() throws Exception {
        String owner = "liked-songs-owner";
        mockMvc.perform(get("/playlists").with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk());

        String likedSongsId = playlistRepository.findByOwnerUserIdAndLikedSongsTrue(owner)
                .orElseThrow()
                .getId()
                .toString();

        mockMvc.perform(delete("/playlists/{id}", likedSongsId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        mockMvc.perform(patch("/playlists/{id}", likedSongsId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Favorites","description":"Nope"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossUserAccessReturnsNotFound() throws Exception {
        String playlistId = createPlaylist("owner-user", "Owner Only", null)
                .get("id")
                .asText();

        mockMvc.perform(get("/playlists/{id}", playlistId).with(jwt().jwt(jwt -> jwt.subject("other-user"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/playlists/{id}/tracks", playlistId)
                        .with(jwt().jwt(jwt -> jwt.subject("other-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"songId":"song-a"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/playlists").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsOperational() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private JsonNode createPlaylist(String owner, String name, String description) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateBody(name, description));
        String response = mockMvc.perform(post("/playlists")
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private record CreateBody(String name, String description) {
    }
}
