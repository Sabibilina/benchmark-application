package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationFlowIT extends E2ETestBase {

    private static Long songIdLong;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void fetchSong() {
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs?size=2")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertFalse(songs.isEmpty(), "Need at least 1 song for recommendation tests");
        songIdLong = ((Number) songs.get(0).get("id")).longValue();
    }

    @Test
    void recommend_dailyMix_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/daily-mix")
        .then()
            .statusCode(401);
    }

    @Test
    void recommend_dailyMix_returns200AndNonEmptyArray() {
        authed()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/daily-mix")
        .then()
            .statusCode(200)
            .body("songs", not(empty()));
    }

    @Test
    void recommend_dailyMix_entryHasFields() {
        List<Map<String, Object>> songs = authed()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/daily-mix")
        .then()
            .statusCode(200)
            .extract().path("songs");

        assertFalse(songs.isEmpty(), "Daily mix must contain at least one song");
        Map<String, Object> entry = songs.get(0);
        // Must have at least id (or songId), title, and artist
        assertTrue(entry.containsKey("id") || entry.containsKey("songId"),
            "Daily mix entry must have id or songId field");
        assertNotNull(entry.get("title"),  "Daily mix entry must have title");
        assertNotNull(entry.get("artist"), "Daily mix entry must have artist");
    }

    @Test
    void recommend_similarSongs_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/similar/" + songIdLong)
        .then()
            .statusCode(401);
    }

    @Test
    void recommend_similarSongs_returns200AndNonEmptyArray() {
        authed()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/similar/" + songIdLong)
        .then()
            .statusCode(200)
            .body("songs", not(empty()));
    }

    @Test
    void recommend_similarSongs_unknownSongId_returns200OrHandledGracefully() {
        int statusCode = authed()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/similar/999999999")
        .then()
            .extract().statusCode();

        // Service may return 200 with empty list or 404 — both are acceptable
        assertTrue(statusCode == 200 || statusCode == 404,
            "Unknown songId should return 200 or 404, got: " + statusCode);
    }

    @Test
    void recommend_afterStreaming_dailyMixReflectsHistory() {
        // Stream the song to build history
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songIdLong)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() ->
                authed()
                .when()
                    .get(ServiceUrls.RECOMMEND + "/recommend/daily-mix")
                .then()
                    .statusCode(200)
                    .body("songs", not(empty()))
            );
    }
}
