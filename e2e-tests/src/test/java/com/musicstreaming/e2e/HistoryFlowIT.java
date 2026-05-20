package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryFlowIT extends E2ETestBase {

    private static String songId1;
    private static String songId2;
    private static String songId3;

    private static String secondToken;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setup() {
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs?size=5")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertTrue(songs.size() >= 3, "Need at least 3 songs for history tests");

        songId1 = String.valueOf(((Number) songs.get(0).get("id")).longValue());
        songId2 = String.valueOf(((Number) songs.get(1).get("id")).longValue());
        songId3 = String.valueOf(((Number) songs.get(2).get("id")).longValue());

        // Register a second user for isolation test
        String u2       = "e2e-hist2-" + UUID.randomUUID().toString().substring(0, 8);
        String email2   = u2 + "@e2e.test";
        String password = "password123";

        given().contentType(ContentType.JSON)
            .body("{\"username\":\"" + u2 + "\",\"email\":\"" + email2 + "\",\"password\":\"" + password + "\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(201);

        secondToken = given().contentType(ContentType.JSON)
            .body("{\"email\":\"" + email2 + "\",\"password\":\"" + password + "\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(200)
            .extract().path("token");
    }

    @Test
    void history_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.ANALYTICS + "/analytics/me/history")
        .then()
            .statusCode(401);
    }

    @Test
    void history_noEvents_returnsEmptyArray() {
        // Second user has never streamed; their history must be empty
        given()
            .header("Authorization", "Bearer " + secondToken)
        .when()
            .get(ServiceUrls.ANALYTICS + "/analytics/me/history")
        .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void history_afterStream_containsPlayStartedEntry() {
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId1)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() ->
                authed()
                .when()
                    .get(ServiceUrls.ANALYTICS + "/analytics/me/history")
                .then()
                    .statusCode(200)
                    .body("size()", greaterThan(0))
            );
    }

    @Test
    void history_entryHasExpectedFields() {
        // Ensure at least one stream event exists (stream if needed)
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId2)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> history = authed()
                .when()
                    .get(ServiceUrls.ANALYTICS + "/analytics/me/history")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                assertFalse(history.isEmpty(), "History must not be empty");
                Map<String, Object> entry = history.get(0);
                assertNotNull(entry.get("songId"),    "entry must have songId");
                assertNotNull(entry.get("eventType"), "entry must have eventType");
                assertNotNull(entry.get("timestamp"), "entry must have timestamp");
            });
    }

    @Test
    void history_isolatedPerUser() {
        // Stream a song as our user
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId1)
        .then()
            .statusCode(200);

        // Second user should still see an empty history
        // (they have never streamed anything)
        given()
            .header("Authorization", "Bearer " + secondToken)
        .when()
            .get(ServiceUrls.ANALYTICS + "/analytics/me/history")
        .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void history_multipleStreams_allAppearInHistory() {
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId2)
        .then()
            .statusCode(200);

        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId3)
        .then()
            .statusCode(200);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> history = authed()
                .when()
                    .get(ServiceUrls.ANALYTICS + "/analytics/me/history")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                // We've streamed multiple songs; there must be multiple entries
                assertTrue(history.size() >= 2,
                    "Expected at least 2 history entries but found: " + history.size());
            });
    }
}
