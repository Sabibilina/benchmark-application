package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class StreamingFlowIT extends E2ETestBase {

    private static String songId;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void fetchSongId() {
        Map<String, Object> page = authed()
        .when()
            .get(ServiceUrls.CATALOG + "/catalog/songs")
        .then()
            .statusCode(200)
            .extract().as(Map.class);

        List<Map<String, Object>> songs = (List<Map<String, Object>>) page.get("content");
        assertFalse(songs.isEmpty(), "Catalog must contain songs for streaming tests");
        // Streaming service uses songId as a String path variable
        songId = String.valueOf(((Number) songs.get(0).get("id")).longValue());
    }

    @Test
    void stream_getManifest_returns200AndHlsContent() {
        authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId)
        .then()
            .statusCode(200)
            .body(containsString("#EXTM3U"));
    }

    @Test
    void stream_getManifest_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId)
        .then()
            .statusCode(401);
    }

    @Test
    void stream_getSegment_returns200AndBinaryPayload() {
        Response response = authed()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId + "/segment/0")
        .then()
            .statusCode(200)
            .extract().response();

        String contentType = response.getContentType();
        assertTrue(contentType != null && contentType.contains("application/octet-stream"),
            "Expected Content-Type application/octet-stream but got: " + contentType);

        byte[] body = response.getBody().asByteArray();
        assertTrue(body.length > 0, "Segment payload must not be empty");
    }

    @Test
    void stream_getSegment_noToken_returns401() {
        given()
        .when()
            .get(ServiceUrls.STREAM + "/stream/" + songId + "/segment/0")
        .then()
            .statusCode(401);
    }

    @Test
    void stream_complete_returns2xx() {
        authed()
        .when()
            .post(ServiceUrls.STREAM + "/stream/" + songId + "/complete")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test
    void stream_skip_returns2xx() {
        authed()
        .when()
            .post(ServiceUrls.STREAM + "/stream/" + songId + "/skip")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test
    void stream_complete_noToken_returns401() {
        given()
        .when()
            .post(ServiceUrls.STREAM + "/stream/" + songId + "/complete")
        .then()
            .statusCode(401);
    }

    @Test
    void stream_skip_noToken_returns401() {
        given()
        .when()
            .post(ServiceUrls.STREAM + "/stream/" + songId + "/skip")
        .then()
            .statusCode(401);
    }
}
