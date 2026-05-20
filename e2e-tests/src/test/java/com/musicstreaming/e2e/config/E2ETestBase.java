package com.musicstreaming.e2e.config;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;

import static io.restassured.RestAssured.given;

public abstract class E2ETestBase {

    protected static String token;
    protected static String userId;

    @BeforeAll
    static void authenticate() {
        String unique   = UUID.randomUUID().toString().substring(0, 8);
        String username = "e2e-" + unique;
        String email    = username + "@e2e.test";
        String password = "password123";

        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/register")
        .then()
            .statusCode(201);

        token = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post(ServiceUrls.AUTH + "/auth/login")
        .then()
            .statusCode(200)
            .extract().path("token");

        userId = username;
    }

    protected static RequestSpecification authed() {
        return given().header("Authorization", "Bearer " + token);
    }
}
