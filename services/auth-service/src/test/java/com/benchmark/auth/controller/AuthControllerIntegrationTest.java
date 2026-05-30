package com.benchmark.auth.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.auth.repository.UserAccountRepository;
import com.benchmark.auth.support.TestKeyFiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("app.jwt.private-key-path", () -> KEYS.privateKey().toString());
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("app.jwt.issuer", () -> "benchmark-auth-test");
        registry.add("app.jwt.access-token-ttl", () -> "PT1H");
    }

    @Test
    void registerPersistsUserAndReturnsVerifiableJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "new-user@example.com",
                                  "password": "CorrectHorse123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("new-user@example.com"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        jwtDecoder.decode(token);
        var saved = userAccountRepository.findByEmail("new-user@example.com");
        org.assertj.core.api.Assertions.assertThat(saved).isPresent();
        org.assertj.core.api.Assertions.assertThat(saved.get().getPasswordHash()).doesNotContain("CorrectHorse123");
    }

    @Test
    void registerRejectsDuplicateUser() throws Exception {
        String body = """
                {
                  "email": "duplicate@example.com",
                  "password": "CorrectHorse123"
                }
                """;
        mockMvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_user"));
    }

    @Test
    void registerRejectsInvalidPayloadWithValidationDetails() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void loginReturnsTokenForValidCredentialsAndRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "login@example.com",
                                  "password": "CorrectHorse123"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "login@example.com",
                                  "password": "CorrectHorse123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "login@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAuthAndOperationalEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "password": "CorrectHorse123"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/not-public")).andExpect(status().isUnauthorized());
    }
}
