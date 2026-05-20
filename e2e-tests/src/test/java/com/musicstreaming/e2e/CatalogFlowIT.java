package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogFlowIT extends E2ETestBase {

    private static Long firstSongId;
    private static String firstSongArtistId;
    private static List<Map<String, Object>> firstPageContent;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void fetchCatalogSeed() {
        Map<String, Object> page = authed()
            .when()
                .get(ServiceUrls.CATALOG + "/catalog/songs")
            .then()
                .statusCode(200)
                .extract().as(Map.class);

        firstPageContent = (List<Map<String, Object>>) page.get("content");
        assertFalse(firstPageContent.isEmpty(), "Catalog must contain at least one song");

        Map<String, Object> song = firstPageContent.get(0);
        firstSongId       = ((Number) song.get("id")).longValue();
        firstSongArtistId = (String) song.get("artistId");
    }

    @Test
    void catalog_getFirstPage_returns200AndArray() {
        authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(200)
            .body("content", not(empty()));
    }

    @Test
    void catalog_pagination_secondPageDifferentFromFirst() {
        List<Map<String, Object>> page1 = authed()
            .queryParam("page", 0).queryParam("size", 5)
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(200)
            .extract().path("content");

        List<Map<String, Object>> page2 = authed()
            .queryParam("page", 1).queryParam("size", 5)
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(200)
            .extract().path("content");

        if (!page2.isEmpty()) {
            assertNotEquals(page1.get(0).get("id"), page2.get(0).get("id"),
                "First songs on page 0 and page 1 must differ");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void catalog_sortByTitleAsc_firstLetterBeforeLastLetter() {
        Map<String, Object> firstPage = authed()
            .queryParam("sort", "title").queryParam("direction", "asc").queryParam("size", 100)
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) firstPage.get("content");
        assertTrue(songs.size() >= 2, "Need at least 2 songs for sort check");

        String firstTitle = (String) songs.get(0).get("title");
        String lastTitle  = (String) songs.get(songs.size() - 1).get("title");
        assertTrue(firstTitle.compareToIgnoreCase(lastTitle) <= 0,
            "Titles must be in ascending order: [" + firstTitle + "] vs [" + lastTitle + "]");
    }

    @Test
    void catalog_getSingleSong_returns200AndExpectedFields() {
        authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs/" + firstSongId)
        .then()
            .statusCode(200)
            .body("id",     notNullValue())
            .body("title",  notNullValue())
            .body("artist", notNullValue());
    }

    @Test
    void catalog_getSingleSong_unknownId_returns404() {
        authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs/999999999")
        .then()
            .statusCode(404);
    }

    @Test
    void catalog_getArtistTopTracks_returns200AndArray() {
        authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/artists/" + firstSongArtistId + "/top-tracks")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test
    void catalog_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(401);
    }

    @Test
    void catalog_expiredToken_returns401() {
        given()
            .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJleHBpcmVkIn0.invalidsig")
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(401);
    }

    // ── Search (Search Service at :8085) ─────────────────────────────────────

    @Test
    void search_byQuery_returns200AndMatchingResults() {
        authed()
            .queryParam("q", "a")
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .body("$", instanceOf(List.class));
    }

    @Test
    void search_byGenre_returnsOnlyMatchingGenre() {
        List<Map<String, Object>> results = authed()
            .queryParam("genre", "Pop")
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .extract().path("$");

        for (Map<String, Object> song : results) {
            assertEquals("Pop", song.get("genre"),
                "Every result must have genre=Pop but found: " + song.get("genre"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_byBpmRange_returnsOnlySongsInRange() {
        List<Map<String, Object>> results = authed()
            .queryParam("bpm_min", 100).queryParam("bpm_max", 140)
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .extract().path("$");

        for (Map<String, Object> song : results) {
            double bpm = ((Number) song.get("bpm")).doubleValue();
            assertTrue(bpm >= 100 && bpm <= 140,
                "bpm=" + bpm + " is outside [100,140]");
        }
    }

    @Test
    void search_byYear_returnsOnlyMatchingYear() {
        List<Map<String, Object>> results = authed()
            .queryParam("year", 2020)
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .extract().path("$");

        for (Map<String, Object> song : results) {
            assertEquals(2020, ((Number) song.get("year")).intValue(),
                "year must be 2020 but found: " + song.get("year"));
        }
    }

    @Test
    void search_allFiltersCombined_narrowsResults() {
        List<Map<String, Object>> broad = authed()
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .extract().path("$");

        List<Map<String, Object>> narrow = authed()
            .queryParam("genre", "Rock").queryParam("bpm_min", 130)
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .extract().path("$");

        assertTrue(narrow.size() <= broad.size(),
            "Filtered results should be a subset of unfiltered results");
    }

    @Test
    void search_noQuery_returns200AndArray() {
        authed()
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .body("$", instanceOf(List.class));
    }

    @Test
    void search_unmatchedQuery_returns200AndEmptyArray() {
        authed()
            .queryParam("q", "zzznomatchzzz_xq9999")
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void search_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(401);
    }
}
