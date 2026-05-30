package com.benchmark.catalog.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.catalog.repository.SongRepository;
import com.benchmark.catalog.support.TestDataFiles;
import com.benchmark.catalog.support.TestKeyFiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerIntegrationTest {

    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();
    private static final java.nio.file.Path DATASET = TestDataFiles.catalogCsv();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SongRepository songRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("app.catalog.dataset-path", DATASET::toString);
        registry.add("app.catalog.ingestion-enabled", () -> "true");
        registry.add("app.catalog.default-page-size", () -> "20");
        registry.add("app.catalog.max-page-size", () -> "100");
    }

    @Test
    void catalogEndpointsRequireJwt() throws Exception {
        mockMvc.perform(get("/catalog/songs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/catalog/songs/test-song-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSongsReturnsPersistedPaginatedDataset() throws Exception {
        mockMvc.perform(get("/catalog/songs?page=0&size=1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));

        org.assertj.core.api.Assertions.assertThat(songRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getSongReturnsDetailsAndMissingSongReturnsNotFound() throws Exception {
        mockMvc.perform(get("/catalog/songs/test-song-1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-song-1"))
                .andExpect(jsonPath("$.title").value("First Test Song"))
                .andExpect(jsonPath("$.artist").value("Test Artist"))
                .andExpect(jsonPath("$.genre").value("pop"))
                .andExpect(jsonPath("$.metadata.track_name").value("First Test Song"));

        mockMvc.perform(get("/catalog/songs/missing-song").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("song_not_found"));
    }

    @Test
    void healthEndpointIsOperational() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
