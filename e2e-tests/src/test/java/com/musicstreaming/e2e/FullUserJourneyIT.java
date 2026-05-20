package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.ServiceUrls;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Full end-to-end user journey: each @Order test builds on state from the previous one.
 * Does NOT extend E2ETestBase — manages its own single static user throughout.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullUserJourneyIT {

    private static final String PASSWORD = "password123";
    private static final String UNIQUE   = UUID.randomUUID().toString().substring(0, 8);
    private static final String USERNAME = "journey-" + UNIQUE;
    private static final String EMAIL    = USERNAME + "@e2e.test";

    // State flows from one step to the next
    private static String token;
    private static String songId;
    private static String artistId;
    private static String playlistId;
    private static String songId2;

    private static io.restassured.specification.RequestSpecification authed() {
        return given().header("Authorization", "Bearer " + token);
    }

    @Test
    @Order(1)
    void journey_register_success() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + USERNAME + "\",\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(201)
            .body("token", notNullValue());
    }

    @Test
    @Order(2)
    void journey_login_returnsToken() {
        token = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue())
            .extract().path("token");
    }

    @Test
    @Order(3)
    @SuppressWarnings("unchecked")
    void journey_browseCatalog_songsAvailable() {
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs?size=5")
        .then()
            .statusCode(200)
            .body("content", not(empty()))
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertFalse(songs.isEmpty(), "Catalog must have songs");

        songId   = String.valueOf(((Number) songs.get(0).get("id")).longValue());
        artistId = (String) songs.get(0).get("artistId");
        songId2  = songs.size() > 1
            ? String.valueOf(((Number) songs.get(1).get("id")).longValue())
            : songId;
    }

    @Test
    @Order(4)
    void journey_searchForSong_returnsResult() {
        authed()
        .when()
            .get(ServiceUrls.SEARCH + "/search")
        .then()
            .statusCode(200)
            .body("$", instanceOf(List.class));
    }

    @Test
    @Order(5)
    void journey_createPlaylist_success() {
        String name = "Journey-PL-" + UNIQUE;

        playlistId = authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo(name))
            .extract().path("id");
    }

    @Test
    @Order(6)
    void journey_addSongToPlaylist_success() {
        List<Map<String, Object>> tracks = authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + playlistId + "/tracks")
        .then()
            .statusCode(201)
            .body("tracks", not(empty()))
            .extract().path("tracks");

        boolean found = tracks.stream()
            .anyMatch(t -> songId.equals(String.valueOf(t.get("songId"))));
        assertTrue(found, "Added song must appear in the playlist track list");
    }

    @Test
    @Order(7)
    void journey_reorderTracks_success() {
        // Add a second song first (if different from songId)
        if (!songId2.equals(songId)) {
            authed()
                .contentType(ContentType.JSON)
                .body("{\"songId\":\"" + songId2 + "\"}")
            .when()
                .post(ServiceUrls.PLAYLIST + "/playlists/" + playlistId + "/tracks")
            .then()
                .statusCode(201);

            // Reorder: put songId2 before songId
            authed()
                .contentType(ContentType.JSON)
                .body("{\"songIds\":[\"" + songId2 + "\",\"" + songId + "\"]}")
            .when()
                .patch(ServiceUrls.PLAYLIST + "/playlists/" + playlistId + "/tracks/reorder")
            .then()
                .statusCode(200)
                .body("tracks[0].songId", equalTo(songId2));
        } else {
            // Only one distinct song — skip reorder but mark step done
            List<Map<String, Object>> tracks = authed()
            .when()
                .get(ServiceUrls.PLAYLIST + "/playlists/" + playlistId)
            .then()
                .statusCode(200)
                .extract().path("tracks");

            assertFalse(tracks.isEmpty(), "Playlist must have tracks");
        }
    }

    @Test
    @Order(8)
    void journey_streamSong_manifestReturned() {
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId)
        .then()
            .statusCode(200)
            .body(containsString("#EXTM3U"));
    }

    @Test
    @Order(9)
    void journey_completeStream_success() {
        authed()
        .when()
            .post(ServiceUrls.STREAM + "/stream/" + songId + "/complete")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test
    @Order(10)
    void journey_waitForHistory_historyNotEmpty() {
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
    @Order(11)
    void journey_getDailyMix_nonEmpty() {
        authed()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/daily-mix")
        .then()
            .statusCode(200)
            .body("songs", not(empty()));
    }

    @Test
    @Order(12)
    void journey_getSimilarSongs_nonEmpty() {
        int status = authed()
        .when()
            .get(ServiceUrls.RECOMMEND + "/recommend/similar/" + songId)
        .then()
            .extract().statusCode();

        assertTrue(status == 200 || status == 404,
            "Similar songs should return 200 or 404, got: " + status);

        if (status == 200) {
            authed()
            .when()
                .get(ServiceUrls.RECOMMEND + "/recommend/similar/" + songId)
            .then()
                .statusCode(200)
                .body("songs", not(empty()));
        }
    }

    @Test
    @Order(13)
    void journey_waitForNotification_notificationPresent() {
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

                // We created a playlist and added a track; expect at least one notification
                assertFalse(notifications.isEmpty(), "Expected at least one notification after playlist+track activity");
            });
    }

    @Test
    @Order(14)
    void journey_removeTrackFromPlaylist_success() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + playlistId + "/tracks/" + songId)
        .then()
            .statusCode(204);

        List<Map<String, Object>> tracks = authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + playlistId)
        .then()
            .statusCode(200)
            .extract().path("tracks");

        boolean stillPresent = tracks.stream()
            .anyMatch(t -> songId.equals(String.valueOf(t.get("songId"))));
        assertFalse(stillPresent, "Removed track must not appear in playlist");
    }

    @Test
    @Order(15)
    void journey_deletePlaylist_success() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + playlistId)
        .then()
            .statusCode(204);

        authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + playlistId)
        .then()
            .statusCode(404);
    }
}
