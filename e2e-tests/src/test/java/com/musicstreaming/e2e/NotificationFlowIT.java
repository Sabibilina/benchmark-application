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

class NotificationFlowIT extends E2ETestBase {

    private static String secondToken;
    private static String songId;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setup() {
        // Fetch a song ID for track-add tests
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs?size=2")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertFalse(songs.isEmpty(), "Need at least 1 song");
        songId = String.valueOf(((Number) songs.get(0).get("id")).longValue());

        // Register second user for isolation test
        String u2       = "e2e-notif2-" + UUID.randomUUID().toString().substring(0, 8);
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
    void notifications_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.NOTIFICATION + "/notifications")
        .then()
            .statusCode(401);
    }

    @Test
    void notifications_emptyForNewUser_returns200AndEmptyArray() {
        // Second user has not created any playlists yet
        given()
            .header("Authorization", "Bearer " + secondToken)
        .when()
            .get(ServiceUrls.NOTIFICATION + "/notifications")
        .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void notifications_afterPlaylistCreated_notificationExists() {
        String name = "Notif-PL-" + UUID.randomUUID().toString().substring(0, 8);
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() ->
                authed()
                .when()
                    .get(ServiceUrls.NOTIFICATION + "/notifications")
                .then()
                    .statusCode(200)
                    .body("size()", greaterThan(0))
            );
    }

    @Test
    void notifications_notificationHasType_PLAYLIST_CREATED() {
        String name = "Notif-PL2-" + UUID.randomUUID().toString().substring(0, 8);
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> notifications = authed()
                .when()
                    .get(ServiceUrls.NOTIFICATION + "/notifications")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                boolean found = notifications.stream()
                    .anyMatch(n -> "PLAYLIST_CREATED".equals(n.get("type")));
                assertTrue(found, "Expected a PLAYLIST_CREATED notification");
            });
    }

    @Test
    void notifications_afterTrackAdded_notificationExists() {
        // Create a playlist then add a track
        String plName = "Notif-Track-" + UUID.randomUUID().toString().substring(0, 8);
        String plId = authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + plName + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201)
            .extract().path("id");

        authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + plId + "/tracks")
        .then()
            .statusCode(201);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> notifications = authed()
                .when()
                    .get(ServiceUrls.NOTIFICATION + "/notifications")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                boolean found = notifications.stream()
                    .anyMatch(n -> "TRACK_ADDED".equals(n.get("type")));
                assertTrue(found, "Expected a TRACK_ADDED notification");
            });
    }

    @Test
    void notifications_notificationHasType_TRACK_ADDED() {
        String plName = "Notif-TrackType-" + UUID.randomUUID().toString().substring(0, 8);
        String plId = authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + plName + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201)
            .extract().path("id");

        authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + plId + "/tracks")
        .then()
            .statusCode(201);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> notifications = authed()
                .when()
                    .get(ServiceUrls.NOTIFICATION + "/notifications")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                boolean found = notifications.stream()
                    .anyMatch(n -> "TRACK_ADDED".equals(n.get("type")));
                assertTrue(found, "Expected TRACK_ADDED notification in list");
            });
    }

    @Test
    void notifications_isolatedPerUser() {
        String plName = "Notif-Isolation-" + UUID.randomUUID().toString().substring(0, 8);

        // Our user creates a playlist
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + plName + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201);

        // Second user should still see no notifications (they never created playlists)
        given()
            .header("Authorization", "Bearer " + secondToken)
        .when()
            .get(ServiceUrls.NOTIFICATION + "/notifications")
        .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void notifications_returnedNewestFirst() {
        String pl1Name = "Notif-Order1-" + UUID.randomUUID().toString().substring(0, 8);
        String pl2Name = "Notif-Order2-" + UUID.randomUUID().toString().substring(0, 8);

        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + pl1Name + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201);

        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + pl2Name + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201);

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                List<Map<String, Object>> notifications = authed()
                .when()
                    .get(ServiceUrls.NOTIFICATION + "/notifications")
                .then()
                    .statusCode(200)
                    .extract().path("$");

                assertTrue(notifications.size() >= 2,
                    "Expected at least 2 notifications for ordering check");

                // Verify newest-first: first entry's createdAt >= second entry's createdAt
                String ts0 = (String) notifications.get(0).get("createdAt");
                String ts1 = (String) notifications.get(1).get("createdAt");
                assertNotNull(ts0, "createdAt must be present in notification");
                assertNotNull(ts1, "createdAt must be present in notification");
                // ISO-8601 strings compare lexicographically == chronologically
                assertTrue(ts0.compareTo(ts1) >= 0,
                    "Notifications must be ordered newest-first: " + ts0 + " vs " + ts1);
            });
    }
}
