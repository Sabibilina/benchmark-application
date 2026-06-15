package com.musicstreaming.recommendation.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.recommendation.dto.DailyMixResponse;
import com.musicstreaming.recommendation.dto.SimilarSongsResponse;
import com.musicstreaming.recommendation.model.RecommendationSong;
import com.musicstreaming.recommendation.repository.PlayEventRepository;
import com.musicstreaming.recommendation.repository.RecommendationSongRepository;
import com.musicstreaming.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private RecommendationSongRepository songRepo;
    @Mock private PlayEventRepository playEventRepo;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // dailyMixSize=5, similarSize=3, cacheTtlSeconds=3600
        service = new RecommendationService(songRepo, playEventRepo, redisTemplate, new ObjectMapper(), 5, 3, 3600);
    }

    // ── Daily Mix ─────────────────────────────────────────────────────────────

    @Test
    void dailyMix_returnsNonEmptyList_whenUserHasPlayHistory() {
        lenient().when(valueOps.get(any())).thenReturn(null);
        when(playEventRepo.findTopSongIdsByUser(eq("alice"), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of("1", "2"));
        when(songRepo.findGenresByIds(List.of(1L, 2L))).thenReturn(List.of("pop", "pop"));
        when(songRepo.findByGenreExcluding(eq("pop"), anyList(), any(Pageable.class)))
                .thenReturn(List.of(buildSong(3L, "pop"), buildSong(4L, "pop")));

        DailyMixResponse response = service.getDailyMix("alice");

        assertThat(response.songs()).isNotEmpty();
    }

    @Test
    void dailyMix_returnsNonEmptyList_whenUserHasNoHistory() {
        lenient().when(valueOps.get(any())).thenReturn(null);
        when(playEventRepo.findTopSongIdsByUser(eq("bob"), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of());
        when(playEventRepo.findGlobalTopSongIds(anyInt())).thenReturn(List.of());
        when(songRepo.findRandom(any(Pageable.class))).thenReturn(List.of(buildSong(1L, "rock")));

        DailyMixResponse response = service.getDailyMix("bob");

        assertThat(response.songs()).isNotEmpty();
        verify(songRepo).findRandom(any(Pageable.class));
    }

    @Test
    void dailyMix_returnsCachedResult_whenCacheHit() throws Exception {
        DailyMixResponse cached = new DailyMixResponse(List.of());
        when(valueOps.get("daily-mix:carol")).thenReturn(new ObjectMapper().writeValueAsString(cached));

        DailyMixResponse response = service.getDailyMix("carol");

        assertThat(response.songs()).isEmpty();
        verifyNoInteractions(songRepo, playEventRepo);
    }

    // ── Resilience4j fallback tests ───────────────────────────────────────────
    // These test the fallback methods directly, verifying they produce a valid
    // response via the compute path without any Redis involvement.

    @Test
    void getDailyMixFallback_computesFromDb_whenRedisUnavailable() {
        RuntimeException redisDown = new RuntimeException("Redis connection refused");
        when(playEventRepo.findTopSongIdsByUser(eq("dave"), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of());
        when(playEventRepo.findGlobalTopSongIds(anyInt())).thenReturn(List.of());
        when(songRepo.findRandom(any(Pageable.class))).thenReturn(List.of(buildSong(10L, "rock")));

        DailyMixResponse response = service.getDailyMixFallback("dave", redisDown);

        assertThat(response).isNotNull();
        assertThat(response.songs()).isNotEmpty();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getDailyMixFallback_returnsEmptyList_whenDbAlsoHasNoData() {
        RuntimeException redisDown = new RuntimeException("Redis unavailable");
        when(playEventRepo.findTopSongIdsByUser(eq("nobody"), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of());
        when(playEventRepo.findGlobalTopSongIds(anyInt())).thenReturn(List.of());
        when(songRepo.findRandom(any(Pageable.class))).thenReturn(List.of());

        DailyMixResponse response = service.getDailyMixFallback("nobody", redisDown);

        assertThat(response).isNotNull();
        assertThat(response.songs()).isEmpty();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getSimilarSongsFallback_computesFromDb_whenRedisUnavailable() {
        RuntimeException redisDown = new RuntimeException("Redis timeout");
        RecommendationSong seed = buildSong(5L, "jazz");
        seed.setTempo(110.0);
        when(songRepo.findById(5L)).thenReturn(Optional.of(seed));
        when(songRepo.findSimilar(eq("jazz"), eq(110.0), eq(20.0), eq(5L), any(Pageable.class)))
                .thenReturn(List.of(buildSong(6L, "jazz")));

        SimilarSongsResponse response = service.getSimilarSongsFallback(5L, redisDown);

        assertThat(response).isNotNull();
        assertThat(response.songs()).isNotEmpty();
        assertThat(response.seedSongId()).isEqualTo(5L);
        verifyNoInteractions(redisTemplate);
    }

    // ── Similar Songs ─────────────────────────────────────────────────────────

    @Test
    void similar_returnsNonEmptyList_whenSongFound() {
        lenient().when(valueOps.get(any())).thenReturn(null);
        RecommendationSong seed = buildSong(5L, "jazz");
        seed.setTempo(110.0);
        when(songRepo.findById(5L)).thenReturn(Optional.of(seed));
        when(songRepo.findSimilar(eq("jazz"), eq(110.0), eq(20.0), eq(5L), any(Pageable.class)))
                .thenReturn(List.of(buildSong(6L, "jazz"), buildSong(7L, "jazz")));

        SimilarSongsResponse response = service.getSimilarSongs(5L);

        assertThat(response.songs()).isNotEmpty();
        assertThat(response.seedSongId()).isEqualTo(5L);
    }

    @Test
    void similar_returnsNonEmptyList_whenSongNotFound() {
        lenient().when(valueOps.get(any())).thenReturn(null);
        when(songRepo.findById(999L)).thenReturn(Optional.empty());
        when(songRepo.findRandom(any(Pageable.class))).thenReturn(List.of(buildSong(1L, "pop")));

        SimilarSongsResponse response = service.getSimilarSongs(999L);

        assertThat(response.songs()).isNotEmpty();
    }

    @Test
    void similar_returnsCachedResult_whenCacheHit() throws Exception {
        SimilarSongsResponse cached = new SimilarSongsResponse(42L, List.of());
        when(valueOps.get("similar:42")).thenReturn(new ObjectMapper().writeValueAsString(cached));

        SimilarSongsResponse response = service.getSimilarSongs(42L);

        assertThat(response.seedSongId()).isEqualTo(42L);
        verifyNoInteractions(songRepo, playEventRepo);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RecommendationSong buildSong(Long id, String genre) {
        RecommendationSong s = new RecommendationSong();
        s.setId(id);
        s.setTrackId("track-" + id);
        s.setTitle("Song " + id);
        s.setArtist("Artist " + id);
        s.setGenre(genre);
        s.setTempo(120.0);
        s.setYear(2020);
        return s;
    }
}
