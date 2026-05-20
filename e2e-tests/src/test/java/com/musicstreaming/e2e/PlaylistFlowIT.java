package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaylistFlowIT extends E2ETestBase {

    // State shared across ordered tests
    private static String likedSongsId;
    private static String mainPlaylistId;
    private static String mainPlaylistName;

    private static String secondToken;
    private static String secondUserPlaylistId;

    private static String songId1;
    private static String songId2;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setup() {
        // Fetch two song IDs from catalog
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs?size=5")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertTrue(songs.size() >= 2, "Catalog must have at least 2 songs");
        songId1 = String.valueOf(((Number) songs.get(0).get("id")).longValue());
        songId2 = String.valueOf(((Number) songs.get(1).get("id")).longValue());

        // Register a second user (for isolation tests)
        String u2       = "e2e-pl2-" + UUID.randomUUID().toString().substring(0, 8);
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

        // Create a playlist owned by the second user
        secondUserPlaylistId = given()
            .header("Authorization", "Bearer " + secondToken)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"SecondUserPlaylist\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201)
            .extract().path("id");
    }

    // ── Listing ──────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void playlists_firstAccess_autoCreatesLikedSongs() {
        List<Map<String, Object>> playlists = authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(200)
            .body("$", not(empty()))
            .extract().path("$");

        Map<String, Object> liked = playlists.stream()
            .filter(p -> Boolean.TRUE.equals(p.get("likedSongs")))
            .findFirst()
            .orElse(null);

        assertNotNull(liked, "Expected a 'Liked Songs' playlist to be auto-created on first access");
        likedSongsId = (String) liked.get("id");
    }

    @Test
    @Order(2)
    void playlists_listReturnsOnlyOwnedPlaylists() {
        // Our user has Liked Songs; second user has their own playlists
        List<Map<String, Object>> ourPlaylists = authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(200)
            .extract().path("$");

        List<Map<String, Object>> theirPlaylists = given()
            .header("Authorization", "Bearer " + secondToken)
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(200)
            .extract().path("$");

        List<String> ourIds   = ourPlaylists.stream().map(p -> (String) p.get("id")).toList();
        List<String> theirIds = theirPlaylists.stream().map(p -> (String) p.get("id")).toList();

        // secondUserPlaylistId must appear only in second user's list
        assertTrue(theirIds.contains(secondUserPlaylistId),
            "Second user's playlist must appear in their own list");
        assertFalse(ourIds.contains(secondUserPlaylistId),
            "Second user's playlist must NOT appear in our list");
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void playlists_create_returns201AndId() {
        mainPlaylistName = "E2E-Playlist-" + UUID.randomUUID().toString().substring(0, 8);

        mainPlaylistId = authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + mainPlaylistName + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo(mainPlaylistName))
            .extract().path("id");
    }

    @Test
    @Order(4)
    void playlists_create_duplicateName_returns409() {
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + mainPlaylistName + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(409);
    }

    @Test
    @Order(5)
    void playlists_create_nameIsLikedSongs_returns400() {
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Liked Songs\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(400);
    }

    @Test
    @Order(6)
    void playlists_create_noToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Unauthorized Playlist\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(401);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void playlists_getById_returns200AndCorrectName() {
        authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId)
        .then()
            .statusCode(200)
            .body("id", equalTo(mainPlaylistId))
            .body("name", equalTo(mainPlaylistName));
    }

    @Test
    @Order(8)
    void playlists_getById_otherUsersPlaylist_returns404() {
        authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + secondUserPlaylistId)
        .then()
            .statusCode(404);
    }

    @Test
    @Order(9)
    void playlists_getById_unknownId_returns404() {
        authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void playlists_patch_updatesName() {
        String newName = "Renamed-" + UUID.randomUUID().toString().substring(0, 8);

        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + newName + "\"}")
        .when()
            .patch(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId)
        .then()
            .statusCode(200)
            .body("name", equalTo(newName));

        mainPlaylistName = newName;
    }

    @Test
    @Order(11)
    void playlists_patch_otherUsersPlaylist_returns404() {
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Hacked\"}")
        .when()
            .patch(ServiceUrls.PLAYLIST + "/playlists/" + secondUserPlaylistId)
        .then()
            .statusCode(404);
    }

    @Test
    @Order(12)
    void playlists_patch_renameLikedSongs_returns400() {
        authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Not Liked Songs\"}")
        .when()
            .patch(ServiceUrls.PLAYLIST + "/playlists/" + likedSongsId)
        .then()
            .statusCode(400);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    @Order(13)
    void playlists_delete_returns204_thenGetReturns404() {
        String deleteId = authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"ToDelete-" + UUID.randomUUID().toString().substring(0, 8) + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists")
        .then()
            .statusCode(201)
            .extract().path("id");

        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + deleteId)
        .then()
            .statusCode(204);

        authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + deleteId)
        .then()
            .statusCode(404);
    }

    @Test
    @Order(14)
    void playlists_delete_otherUsersPlaylist_returns404() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + secondUserPlaylistId)
        .then()
            .statusCode(404);
    }

    @Test
    @Order(15)
    void playlists_delete_likedSongs_returns400() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + likedSongsId)
        .then()
            .statusCode(400);
    }

    // ── Track management ─────────────────────────────────────────────────────

    @Test
    @Order(16)
    void playlists_addTrack_returns201AndTrackInList() {
        List<Map<String, Object>> tracks = authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId1 + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks")
        .then()
            .statusCode(201)
            .body("tracks", not(empty()))
            .extract().path("tracks");

        boolean found = tracks.stream()
            .anyMatch(t -> songId1.equals(String.valueOf(t.get("songId"))));
        assertTrue(found, "Added song " + songId1 + " must appear in track list");
    }

    @Test
    @Order(17)
    void playlists_addTrack_positionIsZeroForFirstTrack() {
        List<Map<String, Object>> tracks = authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId)
        .then()
            .statusCode(200)
            .extract().path("tracks");

        Map<String, Object> firstTrack = tracks.stream()
            .filter(t -> songId1.equals(String.valueOf(t.get("songId"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("songId1 not found in tracks"));

        assertEquals(0, ((Number) firstTrack.get("position")).intValue(),
            "First track must have position 0");
    }

    @Test
    @Order(18)
    void playlists_addTrack_secondTrackPosition1() {
        List<Map<String, Object>> tracks = authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId2 + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks")
        .then()
            .statusCode(201)
            .extract().path("tracks");

        Map<String, Object> secondTrack = tracks.stream()
            .filter(t -> songId2.equals(String.valueOf(t.get("songId"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("songId2 not found in tracks"));

        assertEquals(1, ((Number) secondTrack.get("position")).intValue(),
            "Second track must have position 1");
    }

    @Test
    @Order(19)
    void playlists_addTrack_duplicateSongId_returns409() {
        authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId1 + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks")
        .then()
            .statusCode(409);
    }

    @Test
    @Order(20)
    void playlists_addTrack_otherUsersPlaylist_returns404() {
        authed()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId1 + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + secondUserPlaylistId + "/tracks")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(21)
    void playlists_addTrack_noToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"songId\":\"" + songId1 + "\"}")
        .when()
            .post(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks")
        .then()
            .statusCode(401);
    }

    // ── Reorder ──────────────────────────────────────────────────────────────

    @Test
    @Order(22)
    void playlists_reorder_updatesPositions() {
        // Swap songId1 and songId2: put songId2 first
        List<Map<String, Object>> tracks = authed()
            .contentType(ContentType.JSON)
            .body("{\"songIds\":[\"" + songId2 + "\",\"" + songId1 + "\"]}")
        .when()
            .patch(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks/reorder")
        .then()
            .statusCode(200)
            .extract().path("tracks");

        Map<String, Object> song2Track = tracks.stream()
            .filter(t -> songId2.equals(String.valueOf(t.get("songId"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("songId2 not in tracks after reorder"));

        Map<String, Object> song1Track = tracks.stream()
            .filter(t -> songId1.equals(String.valueOf(t.get("songId"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("songId1 not in tracks after reorder"));

        assertEquals(0, ((Number) song2Track.get("position")).intValue(),
            "songId2 should now be at position 0");
        assertEquals(1, ((Number) song1Track.get("position")).intValue(),
            "songId1 should now be at position 1");
    }

    @Test
    @Order(23)
    void playlists_reorder_mismatchedSongIds_returns400() {
        String nonexistentId = "999999";
        authed()
            .contentType(ContentType.JSON)
            .body("{\"songIds\":[\"" + nonexistentId + "\",\"" + songId1 + "\"]}")
        .when()
            .patch(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks/reorder")
        .then()
            .statusCode(400);
    }

    // ── Remove track ─────────────────────────────────────────────────────────

    @Test
    @Order(24)
    void playlists_removeTrack_returns204_thenTrackAbsent() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks/" + songId1)
        .then()
            .statusCode(204);

        List<Map<String, Object>> tracks = authed()
        .when()
            .get(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId)
        .then()
            .statusCode(200)
            .extract().path("tracks");

        boolean stillPresent = tracks.stream()
            .anyMatch(t -> songId1.equals(String.valueOf(t.get("songId"))));
        assertFalse(stillPresent, "Removed track must not appear in playlist");
    }

    @Test
    @Order(25)
    void playlists_removeTrack_nonexistentSong_returns404() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + mainPlaylistId + "/tracks/999999")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(26)
    void playlists_removeTrack_otherUsersPlaylist_returns404() {
        authed()
        .when()
            .delete(ServiceUrls.PLAYLIST + "/playlists/" + secondUserPlaylistId + "/tracks/" + songId1)
        .then()
            .statusCode(404);
    }
}
