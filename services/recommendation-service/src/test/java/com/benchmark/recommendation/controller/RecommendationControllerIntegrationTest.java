package com.benchmark.recommendation.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.recommendation.TestKeyFiles;
import com.benchmark.recommendation.dto.RecommendationItemResponse;
import com.benchmark.recommendation.dto.RecommendationResponse;
import com.benchmark.recommendation.service.RecommendationService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.datasource.url=jdbc:h2:mem:recommendation-controller;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "management.health.redis.enabled=false",
        "management.health.db.enabled=false"
})
@AutoConfigureMockMvc
class RecommendationControllerIntegrationTest {

    @TempDir
    static Path keyDir;

    static Path publicKey;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RecommendationService recommendationService;

    @BeforeAll
    static void createKeys() {
        publicKey = TestKeyFiles.writePublicKey(keyDir);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> publicKey.toString());
    }

    @Test
    void rejectsUnauthenticatedRecommendationRequests() throws Exception {
        mockMvc.perform(get("/recommend/daily-mix"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAuthenticatedDailyMix() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.dailyMix(eq(userId), eq(null)))
                .thenReturn(new RecommendationResponse("daily-mix", List.of(
                        new RecommendationItemResponse("song-a", 1, "based on playback history"))));

        mockMvc.perform(get("/recommend/daily-mix")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("daily-mix"))
                .andExpect(jsonPath("$.recommendations[0].songId").value("song-a"));
    }

    @Test
    void returnsSimilarRecommendations() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.similar(eq("song-a"), eq(3)))
                .thenReturn(new RecommendationResponse("similar", List.of(
                        new RecommendationItemResponse("song-b", 1, "users also played"))));

        mockMvc.perform(get("/recommend/similar/song-a?limit=3")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("similar"))
                .andExpect(jsonPath("$.recommendations[0].songId").value("song-b"));
    }

    @Test
    void rejectsNonUuidJwtSubject() throws Exception {
        mockMvc.perform(get("/recommend/daily-mix")
                        .with(jwt().jwt(token -> token.subject("not-a-uuid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JWT subject must be a UUID"));
    }
}
