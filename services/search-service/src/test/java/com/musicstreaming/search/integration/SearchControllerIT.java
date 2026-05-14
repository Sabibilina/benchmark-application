package com.musicstreaming.search.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.search.config.JwtTestHelper;
import com.musicstreaming.search.config.OpenSearchTestContainer;
import com.musicstreaming.search.model.SongDocument;
import com.musicstreaming.search.seed.SearchIndexSeeder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchControllerIT {

    static {
        JwtTestHelper.mintToken("bootstrap");
        OpenSearchTestContainer.INSTANCE.isRunning(); // ensure singleton is started
    }

    @DynamicPropertySource
    static void configureOpenSearch(DynamicPropertyRegistry registry) {
        registry.add("opensearch.host", OpenSearchTestContainer.INSTANCE::getHost);
        registry.add("opensearch.port", () -> OpenSearchTestContainer.INSTANCE.getMappedPort(9200));
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RestHighLevelClient client;

    @Autowired
    private SearchIndexSeeder seeder;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${opensearch.index-name:songs-test}")
    private String indexName;

    private String token;

    // ── Fixed test dataset ────────────────────────────────────────────────────

    private static List<SongDocument> testSongs() {
        return List.of(
                song(1L, "test-001", "Alpha Song",    "Artist One",   "Album A", "Pop",  2020, 120.0),
                song(2L, "test-002", "Beta Track",    "Artist Two",   "Album B", "Rock", 2019, 140.0),
                song(3L, "test-003", "Gamma Beat",    "Artist One",   "Album C", "Pop",  2021, 100.0),
                song(4L, "test-004", "Delta Melody",  "Artist Three", "Album D", "Jazz", 2020,  80.0),
                song(5L, "test-005", "Epsilon Sound", "Artist Two",   "Album E", "Rock", 2018, 160.0)
        );
    }

    private static SongDocument song(Long id, String trackId, String title, String artist,
                                     String album, String genre, int year, double tempo) {
        SongDocument d = new SongDocument();
        d.setSongId(id);
        d.setTrackId(trackId);
        d.setTitle(title);
        d.setArtist(artist);
        d.setAlbum(album);
        d.setGenre(genre);
        d.setYear(year);
        d.setTempo(tempo);
        d.setPopularity(50);
        d.setDurationMs(200_000);
        d.setDanceability(0.5);
        d.setEnergy(0.6);
        d.setExplicit(false);
        d.setCountry("US");
        d.setLabel("TestLabel");
        return d;
    }

    @BeforeAll
    void seedTestData() throws Exception {
        token = JwtTestHelper.mintToken("testuser");

        seeder.ensureIndexExists();

        BulkRequest bulk = new BulkRequest();
        for (SongDocument doc : testSongs()) {
            bulk.add(new IndexRequest(indexName)
                    .id(doc.getTrackId())
                    .source(objectMapper.writeValueAsString(doc), XContentType.JSON));
        }
        client.bulk(bulk, RequestOptions.DEFAULT);

        // Force refresh so all documents are immediately searchable
        client.indices().refresh(new RefreshRequest(indexName), RequestOptions.DEFAULT);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    // ── 401 / auth checks ─────────────────────────────────────────────────────

    @Test
    void search_withoutToken_returns401() throws Exception {
        mvc.perform(get("/search"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_withInvalidToken_returns401() throws Exception {
        mvc.perform(get("/search")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ── Basic query ───────────────────────────────────────────────────────────

    @Test
    void search_noParams_returns200WithResults() throws Exception {
        mvc.perform(get("/search")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    @Test
    void search_withTextQuery_returnsMatchingSong() throws Exception {
        mvc.perform(get("/search")
                        .param("q", "Alpha")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Alpha Song")));
    }

    @Test
    void search_withTextQuery_doesNotReturnUnrelatedSong() throws Exception {
        mvc.perform(get("/search")
                        .param("q", "Alpha")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", not(hasItem("Beta Track"))));
    }

    // ── Genre filter ──────────────────────────────────────────────────────────

    @Test
    void search_byGenre_returnsOnlyMatchingGenre() throws Exception {
        mvc.perform(get("/search")
                        .param("genre", "Pop")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[*].genre", everyItem(is("Pop"))));
    }

    @Test
    void search_byGenre_excludesOtherGenres() throws Exception {
        mvc.perform(get("/search")
                        .param("genre", "Jazz")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Delta Melody")))
                .andExpect(jsonPath("$[*].genre", everyItem(is("Jazz"))));
    }

    // ── BPM range filter ──────────────────────────────────────────────────────

    @Test
    void search_byBpmRange_returnsOnlySongsInRange() throws Exception {
        // Songs with tempo 100, 120, 140 are in range [100, 140]
        mvc.perform(get("/search")
                        .param("bpm_min", "100")
                        .param("bpm_max", "140")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)))
                .andExpect(jsonPath("$[*].bpm", everyItem(allOf(
                        greaterThanOrEqualTo(100.0),
                        lessThanOrEqualTo(140.0)))));
    }

    @Test
    void search_byBpmMinOnly_returnsOnlySongsAboveMin() throws Exception {
        // Songs with tempo >= 150 → only Epsilon Sound (160)
        mvc.perform(get("/search")
                        .param("bpm_min", "150")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Epsilon Sound")))
                .andExpect(jsonPath("$[*].bpm", everyItem(greaterThanOrEqualTo(150.0))));
    }

    // ── Year filter ───────────────────────────────────────────────────────────

    @Test
    void search_byYear_returnsOnlyMatchingYear() throws Exception {
        // Songs from 2020: Alpha Song, Delta Melody
        mvc.perform(get("/search")
                        .param("year", "2020")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[*].year", everyItem(is(2020))));
    }

    // ── Combined filters ──────────────────────────────────────────────────────

    @Test
    void search_combinedFilters_returnsCorrectSubset() throws Exception {
        // q=Gamma AND genre=Pop → only Gamma Beat
        mvc.perform(get("/search")
                        .param("q", "Gamma")
                        .param("genre", "Pop")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].title", is("Gamma Beat")));
    }

    @Test
    void search_responseContainsExpectedFields() throws Exception {
        mvc.perform(get("/search")
                        .param("q", "Alpha")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackId", notNullValue()))
                .andExpect(jsonPath("$[0].title", notNullValue()))
                .andExpect(jsonPath("$[0].artist", notNullValue()))
                .andExpect(jsonPath("$[0].genre", notNullValue()))
                .andExpect(jsonPath("$[0].year", notNullValue()))
                .andExpect(jsonPath("$[0].bpm", notNullValue()));
    }
}
