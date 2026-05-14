package com.benchmark.analytics.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.benchmark.analytics.persistence.AnalyticsEventRecord;
import com.benchmark.analytics.persistence.AnalyticsEventRepository;
import com.benchmark.analytics.persistence.AnalyticsSchemaInitializer;
import com.benchmark.analytics.persistence.GlobalChartRecord;
import com.benchmark.analytics.support.TestKeyFiles;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerIntegrationTest {

    private static final TestKeyFiles.KeyPaths KEYS = TestKeyFiles.create();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AnalyticsEventRepository repository;

    @MockBean
    AnalyticsSchemaInitializer schemaInitializer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key-path", () -> KEYS.publicKey().toString());
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:65534");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("management.health.db.enabled", () -> "false");
    }

    @Test
    void analyticsEndpointsRequireJwtAndRejectInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/analytics/me/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/analytics/charts/global")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/analytics/me/history").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyReturnsOnlyAuthenticatedUserEvents() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(repository.findHistory("user-1", 0, 20)).thenReturn(List.of(
                new AnalyticsEventRecord(eventId, "play.started", "user-1", "song-1", Instant.parse("2026-05-14T10:00:00Z"))));
        when(repository.countHistory("user-1")).thenReturn(1L);

        mockMvc.perform(get("/analytics/me/history").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].userId").value("user-1"))
                .andExpect(jsonPath("$.content[0].songId").value("song-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void globalChartReturnsRankedPlayCountsAndValidationErrors() throws Exception {
        when(repository.findGlobalChart(2)).thenReturn(List.of(
                new GlobalChartRecord("song-1", 4),
                new GlobalChartRecord("song-2", 2)));

        mockMvc.perform(get("/analytics/charts/global?limit=2").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].songId").value("song-1"))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].playCount").value(4));

        mockMvc.perform(get("/analytics/me/history?page=-1").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    @Test
    void healthIsOperational() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
