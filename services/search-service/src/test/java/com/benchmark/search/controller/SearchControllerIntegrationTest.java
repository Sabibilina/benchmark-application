package com.benchmark.search.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.search.indexing.SearchDocument;
import com.benchmark.search.opensearch.OpenSearchIndexClient;
import com.benchmark.search.opensearch.SearchHit;
import com.benchmark.search.opensearch.SearchResult;
import com.benchmark.search.support.TestDataFiles;
import com.benchmark.search.support.TestKeyFiles;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerIntegrationTest {

    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OpenSearchIndexClient indexClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("app.search.opensearch-url", () -> "http://localhost:65534");
        registry.add("app.search.index-name", () -> "songs");
        registry.add("app.search.catalog-dataset-path", () -> TestDataFiles.catalogCsv().toString());
        registry.add("app.search.indexing-enabled", () -> "false");
    }

    @Test
    void searchRequiresJwtAndRejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/search?q=ocean")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/search?q=ocean").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void textSearchReturnsIndexedSongResults() throws Exception {
        when(indexClient.search(eq("songs"), anyMap())).thenReturn(result(song("song-1", "Ocean Drive", "dance", "115.5", 2015)));

        mockMvc.perform(get("/search?q=ocean").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("song-1"))
                .andExpect(jsonPath("$.content[0].title").value("Ocean Drive"))
                .andExpect(jsonPath("$.content[0].genre").value("dance"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void genreBpmYearAndCombinedFiltersReachSearchQuery() throws Exception {
        when(indexClient.search(eq("songs"), anyMap())).thenReturn(result(song("song-2", "Quiet Room", "indie", "92", 2020)));

        mockMvc.perform(get("/search?q=quiet&genre=indie&bpm_min=80&bpm_max=100&year=2020")
                        .with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].genre").value("indie"))
                .andExpect(jsonPath("$.content[0].year").value(2020));

        ArgumentCaptor<java.util.Map<String, Object>> query = ArgumentCaptor.forClass(java.util.Map.class);
        verify(indexClient).search(eq("songs"), query.capture());
        org.assertj.core.api.Assertions.assertThat(query.getValue().toString())
                .contains("quiet", "genre.keyword", "indie", "bpm", "year=2020");
    }

    @Test
    void invalidBpmRangeReturnsBadRequestAndHealthIsOperational() throws Exception {
        mockMvc.perform(get("/search?bpm_min=130&bpm_max=120").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private SearchResult result(SearchDocument document) {
        return new SearchResult(List.of(new SearchHit(document, 2.5)), 1);
    }

    private SearchDocument song(String id, String title, String genre, String bpm, Integer year) {
        return new SearchDocument(id, title, "Artist", "Album", genre, new BigDecimal(bpm), year);
    }
}
