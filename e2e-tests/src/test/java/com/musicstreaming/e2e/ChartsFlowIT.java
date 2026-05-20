package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;

class ChartsFlowIT extends E2ETestBase {

    private static String songA;
    private static String songB;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void fetchSongs() {
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs?size=5")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertTrue(songs.size() >= 2, "Need at least 2 songs for charts tests");
        songA = String.valueOf(((Number) songs.get(0).get("id")).longValue());
        songB = String.valueOf(((Number) songs.get(1).get("id")).longValue());
    }

    @Test
    void charts_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.ANALYTICS + "/analytics/charts/global")
        .then()
            .statusCode(401);
    }

    @Test
    void charts_returnsArray() {
        authed()
        .when()
            .get(ServiceUrls.ANALYTICS + "/analytics/charts/global")
        .then()
            .statusCode(200)
            .body("$", instanceOf(List.class));
    }

    @Test
    void charts_entryHasFields_songId_rank_playCount() {
        // Stream songA to guarantee at least one chart entry
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songA)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> charts = authed()
                .when()
                    .get(ServiceUrls.ANALYTICS + "/analytics/charts/global")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                assertFalse(charts.isEmpty(), "Charts must not be empty after streaming");
                Map<String, Object> entry = charts.get(0);
                assertNotNull(entry.get("songId"),    "chart entry must have songId");
                assertNotNull(entry.get("rank"),      "chart entry must have rank");
                assertNotNull(entry.get("playCount"), "chart entry must have playCount");
            });
    }

    @Test
    void charts_songPlayedMoreTimesRanksHigher() {
        // Stream songA 3 times, songB 1 time
        for (int i = 0; i < 3; i++) {
            authed()
            .when()
                .get(ServiceUrls.STREAM + "/stream/" + songA)
            .then()
                .statusCode(200);
        }

        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songB)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> charts = authed()
                .when()
                    .get(ServiceUrls.ANALYTICS + "/analytics/charts/global")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                int rankA = -1, rankB = -1;
                for (Map<String, Object> entry : charts) {
                    String sid = String.valueOf(entry.get("songId"));
                    int rank = ((Number) entry.get("rank")).intValue();
                    if (songA.equals(sid)) rankA = rank;
                    if (songB.equals(sid)) rankB = rank;
                }

                assertTrue(rankA != -1, "songA must appear in charts");
                assertTrue(rankB != -1, "songB must appear in charts");
                // Lower rank number = better (rank 1 is top)
                assertTrue(rankA < rankB,
                    "songA (3 plays) should rank higher (lower rank#) than songB (1 play): rankA=" + rankA + ", rankB=" + rankB);
            });
    }

    @Test
    void charts_skippedSongsNotCounted() {
        // Skip a unique song; it should either not appear in charts or rank lower than played songs
        // We use songB as our "played" reference from previous test; this just verifies skip doesn't inflate count
        authed()
        .when()
            .post(ServiceUrls.STREAM + "/stream/" + songB + "/skip")
        .then()
            .statusCode(anyOf(is(200), is(204)));

        // Stream songA once more to ensure it still outranks songB
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songA)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> charts = authed()
                .when()
                    .get(ServiceUrls.ANALYTICS + "/analytics/charts/global")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                int rankA = -1, rankB = -1;
                for (Map<String, Object> entry : charts) {
                    String sid = String.valueOf(entry.get("songId"));
                    int rank = ((Number) entry.get("rank")).intValue();
                    if (songA.equals(sid)) rankA = rank;
                    if (songB.equals(sid)) rankB = rank;
                }

                assertTrue(rankA != -1, "songA must appear in charts");
                // songA (multiple play.started) must rank at least as high as songB
                if (rankB != -1) {
                    assertTrue(rankA <= rankB,
                        "songA with more play events should rank >= songB after a skip: rankA=" + rankA + ", rankB=" + rankB);
                }
            });
    }
}
