package com.benchmark.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benchmark.recommendation.config.RecommendationProperties;
import com.benchmark.recommendation.dto.RecommendationResponse;
import com.benchmark.recommendation.entity.UserSongInteraction;
import com.benchmark.recommendation.messaging.PlaybackEvent;
import com.benchmark.recommendation.repository.SongAffinityRepository;
import com.benchmark.recommendation.repository.UserSongInteractionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    UserSongInteractionRepository interactionRepository;

    @Mock
    SongAffinityRepository affinityRepository;

    @Mock
    RecommendationCache cache;

    RecommendationProperties properties = new RecommendationProperties("playback-events", 2, 5, Duration.ofMinutes(5));
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(
                interactionRepository,
                affinityRepository,
                cache,
                properties,
                clock);
    }

    @Test
    void returnsDailyMixFromUserHistoryWithGlobalFallback() {
        UUID userId = UUID.randomUUID();
        when(cache.getDailyMix(userId)).thenReturn(Optional.empty());
        when(interactionRepository.findTopPositiveSongsForUser(userId.toString(), 2)).thenReturn(List.of("song-a"));
        when(interactionRepository.findGlobalPositiveSongs(2)).thenReturn(List.of("song-b", "song-a"));

        RecommendationResponse response = service.dailyMix(userId, null);

        assertThat(response.type()).isEqualTo("daily-mix");
        assertThat(response.recommendations()).extracting("songId").containsExactly("song-a", "song-b");
        verify(cache).putDailyMix(eq(userId), eq(response));
    }

    @Test
    void returnsSimilarRecommendationsFromAffinityWithGlobalFallback() {
        when(affinityRepository.findRelatedSongIds("song-a", 3)).thenReturn(List.of("song-b"));
        when(interactionRepository.findGlobalPositiveSongs(3)).thenReturn(List.of("song-a", "song-c"));

        RecommendationResponse response = service.similar("song-a", 3);

        assertThat(response.type()).isEqualTo("similar");
        assertThat(response.recommendations()).extracting("songId").containsExactly("song-b", "song-c");
    }

    @Test
    void rejectsInvalidLimit() {
        assertThatThrownBy(() -> service.dailyMix(UUID.randomUUID(), 0))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessage("limit must be between 1 and 5");
    }

    @Test
    void recordsPositivePlaybackEventsAndUpdatesAffinity() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlaybackEvent event = new PlaybackEvent(
                eventId.toString(),
                "play.started",
                userId.toString(),
                "song-a",
                Instant.parse("2026-01-01T00:10:00Z"));
        when(interactionRepository.existsByEventId(eventId)).thenReturn(false);
        when(interactionRepository.findOtherPositiveSongsForUser(userId.toString(), "song-a", 5)).thenReturn(List.of("song-b"));

        service.recordPlaybackEvent(event);

        ArgumentCaptor<UserSongInteraction> captor = ArgumentCaptor.forClass(UserSongInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertThat(captor.getValue().isPositiveSignal()).isTrue();
        assertThat(captor.getValue().getUserId()).isEqualTo(userId.toString());
        verify(affinityRepository).incrementAffinity(eq("song-a"), eq("song-b"), eq(clock.instant()));
        verify(affinityRepository).incrementAffinity(eq("song-b"), eq("song-a"), eq(clock.instant()));
        verify(cache).evictDailyMix(userId);
        verify(cache).evictSimilar("song-a");
        verify(cache).evictSimilar("song-b");
    }

    @Test
    void storesSkippedEventsWithoutUpdatingAffinity() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlaybackEvent event = new PlaybackEvent(
                eventId.toString(),
                "play.skipped",
                userId.toString(),
                "song-a",
                Instant.parse("2026-01-01T00:10:00Z"));
        when(interactionRepository.existsByEventId(eventId)).thenReturn(false);

        service.recordPlaybackEvent(event);

        ArgumentCaptor<UserSongInteraction> captor = ArgumentCaptor.forClass(UserSongInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertThat(captor.getValue().isPositiveSignal()).isFalse();
        verify(affinityRepository, never()).incrementAffinity(any(), any(), any());
    }

    @Test
    void ignoresDuplicateEvents() {
        UUID eventId = UUID.randomUUID();
        PlaybackEvent event = new PlaybackEvent(
                eventId.toString(),
                "play.started",
                UUID.randomUUID().toString(),
                "song-a",
                Instant.parse("2026-01-01T00:10:00Z"));
        when(interactionRepository.existsByEventId(eventId)).thenReturn(true);

        service.recordPlaybackEvent(event);

        verify(interactionRepository, never()).save(any());
        verify(affinityRepository, never()).incrementAffinity(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedEventTypes() {
        PlaybackEvent event = new PlaybackEvent(
                UUID.randomUUID().toString(),
                "play.paused",
                UUID.randomUUID().toString(),
                "song-a",
                Instant.parse("2026-01-01T00:10:00Z"));

        assertThatThrownBy(() -> service.recordPlaybackEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported type: play.paused");
        verify(interactionRepository, never()).save(any());
    }
}
