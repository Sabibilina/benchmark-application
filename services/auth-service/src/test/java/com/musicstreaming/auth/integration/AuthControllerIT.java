package com.musicstreaming.auth.integration;

import com.musicstreaming.auth.TestcontainersConfig;
import com.musicstreaming.auth.config.JwtConfig;
import com.musicstreaming.auth.dto.AuthResponse;
import com.musicstreaming.auth.dto.ErrorResponse;
import com.musicstreaming.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtConfig jwtConfig;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // --- POST /auth/register ---

    @Test
    void register_validRequest_returns201WithToken() {
        ResponseEntity<AuthResponse> response = register("alice", "alice@example.com", "password123");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().token());
    }

    @Test
    void register_tokenIsRS256WithCorrectClaims() {
        ResponseEntity<AuthResponse> response = register("alice", "alice@example.com", "password123");

        String token = response.getBody().token();
        Claims claims = Jwts.parser()
                .verifyWith(jwtConfig.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(claims.getSubject());
        assertEquals("alice", claims.get("username", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void register_duplicateUsername_returns409() {
        register("alice", "alice@example.com", "password123");

        ResponseEntity<ErrorResponse> second = restTemplate.postForEntity(
                "/auth/register",
                Map.of("username", "alice", "email", "other@example.com", "password", "password123"),
                ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    void register_duplicateEmail_returns409() {
        register("alice", "alice@example.com", "password123");

        ResponseEntity<ErrorResponse> second = restTemplate.postForEntity(
                "/auth/register",
                Map.of("username", "bob", "email", "alice@example.com", "password", "password123"),
                ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    void register_invalidRequest_returns400() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/auth/register",
                Map.of("username", "", "email", "not-an-email", "password", "short"),
                ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().message());
    }

    // --- POST /auth/login ---

    @Test
    void login_validCredentials_returns200WithToken() {
        register("alice", "alice@example.com", "password123");

        ResponseEntity<AuthResponse> response = login("alice", "password123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().token());
    }

    @Test
    void login_tokenContainsCorrectClaims() {
        register("alice", "alice@example.com", "password123");
        String registerToken = register("alice2", "alice2@example.com", "password123")
                .getBody().token();
        String registerSubject = Jwts.parser()
                .verifyWith(jwtConfig.getPublicKey())
                .build()
                .parseSignedClaims(registerToken)
                .getPayload()
                .getSubject();

        ResponseEntity<AuthResponse> loginResponse = login("alice2", "password123");
        Claims claims = Jwts.parser()
                .verifyWith(jwtConfig.getPublicKey())
                .build()
                .parseSignedClaims(loginResponse.getBody().token())
                .getPayload();

        assertEquals(registerSubject, claims.getSubject());
        assertEquals("alice2", claims.get("username", String.class));
    }

    @Test
    void login_wrongPassword_returns401() {
        register("alice", "alice@example.com", "password123");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/auth/login",
                Map.of("username", "alice", "password", "wrongpassword"),
                ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_unknownUser_returns401() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/auth/login",
                Map.of("username", "nobody", "password", "password123"),
                ErrorResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- Key file presence ---

    @Test
    void publicKeyFile_existsAfterStartup() {
        Path publicPem = Path.of("/tmp/auth-service-test-keys/public.pem");
        assertTrue(Files.exists(publicPem), "public.pem must exist at JWT key directory");
    }

    // --- Helpers ---

    private ResponseEntity<AuthResponse> register(String username, String email, String password) {
        return restTemplate.postForEntity(
                "/auth/register",
                Map.of("username", username, "email", email, "password", password),
                AuthResponse.class);
    }

    private ResponseEntity<AuthResponse> login(String username, String password) {
        return restTemplate.postForEntity(
                "/auth/login",
                Map.of("username", username, "password", password),
                AuthResponse.class);
    }
}
