package com.musicstreaming.e2e;

import com.musicstreaming.e2e.config.E2ETestBase;
import com.musicstreaming.e2e.config.ServiceUrls;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

class AuthFlowIT extends E2ETestBase {

    // register tests use their own unique users; userId from base class is used for duplicate checks

    @Test
    void register_newUser_returns201AndToken() {
        String u = "newauth-" + UUID.randomUUID().toString().substring(0, 8);
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + u + "\",\"email\":\"" + u + "@e2e.test\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(201)
            .body("token", notNullValue());
    }

    @Test
    void register_duplicateUsername_returns409() {
        // userId was registered by @BeforeAll; reuse the same username with a different email
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + userId + "\",\"email\":\"other-" + UUID.randomUUID().toString().substring(0,8) + "@e2e.test\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(409);
    }

    @Test
    void register_duplicateEmail_returns409() {
        String email = userId + "@e2e.test";
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"other-" + UUID.randomUUID().toString().substring(0,8) + "\",\"email\":\"" + email + "\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(409);
    }

    @Test
    void register_missingUsername_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"missing@e2e.test\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(400);
    }

    @Test
    void register_missingPassword_returns400() {
        String u = "nopwd-" + UUID.randomUUID().toString().substring(0, 8);
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + u + "\",\"email\":\"" + u + "@e2e.test\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(400);
    }

    @Test
    void register_invalidEmailFormat_returns400() {
        String u = "bademail-" + UUID.randomUUID().toString().substring(0, 8);
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + u + "\",\"email\":\"not-an-email\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(400);
    }

    @Test
    void login_validCredentials_returns200AndToken() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + userId + "@e2e.test\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(200)
            .body("token", notNullValue());
    }

    @Test
    void login_wrongPassword_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + userId + "@e2e.test\",\"password\":\"wrongpassword\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(401);
    }

    @Test
    void login_unknownEmail_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"nobody@nowhere.test\",\"password\":\"password123\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(401);
    }

    @Test
    void login_missingPassword_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + userId + "@e2e.test\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(400);
    }
}
