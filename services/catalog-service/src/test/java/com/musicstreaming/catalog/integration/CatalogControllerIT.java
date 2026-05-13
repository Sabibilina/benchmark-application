package com.musicstreaming.catalog.integration;

import com.musicstreaming.catalog.TestKeys;
import com.musicstreaming.catalog.TestcontainersConfig;
import com.musicstreaming.catalog.entity.Song;
import com.musicstreaming.catalog.repository.SongRepository;
import com.musicstreaming.catalog.seed.DataSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CatalogControllerIT {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key-path", () -> TestKeys.PUBLIC_KEY_PATH);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SongRepository songRepository;

    @BeforeEach
    void setUp() {
        songRepository.deleteAll();
        for (int i = 1; i <= 10; i++) {
            songRepository.save(buildSong("TRK-TEST%06d".formatted(i), "Song " + i, "Artist " + i));
        }
        // Two extra songs with the same artist for top-tracks test
        songRepository.save(buildSong("TRK-ARTIST001", "Hit 1", "Shared Artist"));
        songRepository.save(buildSong("TRK-ARTIST002", "Hit 2", "Shared Artist"));
    }

    @AfterEach
    void tearDown() {
        songRepository.deleteAll();
    }

    // --- Unauthenticated / bad-token cases ---

    @Test
    void getSongs_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/catalog/songs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getSongs_invalidToken_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/catalog/songs", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders("this.is.not.a.jwt")), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getSongs_expiredToken_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/catalog/songs", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(TestKeys.generateExpiredToken("1"))), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getSongById_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/catalog/songs/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Authenticated happy paths ---

    @Test
    void getSongs_validToken_returns200WithPage() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/catalog/songs", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
        assertThat(response.getBody()).containsKey("totalElements");
        assertThat((Integer) response.getBody().get("totalElements")).isEqualTo(12);
    }

    @Test
    void getSongs_paginationPage0Size5_returns5Items() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/catalog/songs?page=0&size=5", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((java.util.List<?>) response.getBody().get("content"))).hasSize(5);
        assertThat((Integer) response.getBody().get("size")).isEqualTo(5);
        assertThat((Integer) response.getBody().get("page")).isEqualTo(0);
    }

    @Test
    void getSongs_paginationPage1Size5_returnsDifferentItems() {
        ResponseEntity<Map> page0 = restTemplate.exchange(
                "/catalog/songs?page=0&size=5&sort=id&direction=asc", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);
        ResponseEntity<Map> page1 = restTemplate.exchange(
                "/catalog/songs?page=1&size=5&sort=id&direction=asc", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        java.util.List<?> content0 = (java.util.List<?>) page0.getBody().get("content");
        java.util.List<?> content1 = (java.util.List<?>) page1.getBody().get("content");

        assertThat(content0).hasSize(5);
        assertThat(content1).hasSize(5);
        // Pages must be disjoint — verify by comparing first element IDs
        @SuppressWarnings("unchecked")
        Number firstId0 = (Number) ((java.util.Map<String, Object>) content0.get(0)).get("id");
        @SuppressWarnings("unchecked")
        Number firstId1 = (Number) ((java.util.Map<String, Object>) content1.get(0)).get("id");
        assertThat(firstId0.longValue()).isNotEqualTo(firstId1.longValue());
    }

    @Test
    void getSongs_sizeLargerThanMax_cappedAt100() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/catalog/songs?size=999", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) response.getBody().get("size")).isLessThanOrEqualTo(100);
    }

    @Test
    void getSongById_existingId_returns200WithMetadata() {
        Long id = songRepository.findAll().get(0).getId();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/catalog/songs/" + id, HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("title")).isNotNull();
        assertThat(body.get("artist")).isNotNull();
        assertThat(body.get("genre")).isEqualTo("Pop");
        assertThat(body.get("tempo")).isNotNull();
        assertThat(body.get("year")).isEqualTo(2020);
    }

    @Test
    void getSongById_unknownId_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/catalog/songs/999999999", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void getSongById_nonNumericId_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/catalog/songs/abc", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getArtistTopTracks_validArtistId_returnsUpTo10Songs() {
        String artistId = DataSeeder.computeArtistId("Shared Artist");

        ResponseEntity<java.util.List> response = restTemplate.exchange(
                "/catalog/artists/" + artistId + "/top-tracks", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), java.util.List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void getArtistTopTracks_unknownArtistId_returnsEmptyList() {
        ResponseEntity<java.util.List> response = restTemplate.exchange(
                "/catalog/artists/0000000000000000/top-tracks", HttpMethod.GET,
                new HttpEntity<>(validHeaders()), java.util.List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // --- Helpers ---

    private HttpHeaders validHeaders() {
        return bearerHeaders(TestKeys.generateToken("1"));
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Song buildSong(String trackId, String title, String artist) {
        Song song = new Song();
        song.setTrackId(trackId);
        song.setTitle(title);
        song.setArtist(artist);
        song.setArtistId(DataSeeder.computeArtistId(artist));
        song.setAlbum("Test Album");
        song.setReleaseDate(LocalDate.of(2020, 6, 15));
        song.setYear(2020);
        song.setGenre("Pop");
        song.setDurationMs(210000);
        song.setPopularity(75);
        song.setDanceability(0.8);
        song.setEnergy(0.7);
        song.setMusicalKey(5);
        song.setLoudness(-5.5);
        song.setMode(1);
        song.setInstrumentalness(0.0);
        song.setTempo(120.0);
        song.setStreamCount(50000L);
        song.setCountry("US");
        song.setExplicit(false);
        song.setLabel("Test Label");
        return song;
    }
}
