package com.benchmark.recommendation.service;

import com.benchmark.recommendation.config.RecommendationProperties;
import com.benchmark.recommendation.dto.RecommendationItemResponse;
import com.benchmark.recommendation.dto.RecommendationResponse;
import com.benchmark.recommendation.entity.UserSongInteraction;
import com.benchmark.recommendation.messaging.PlaybackEvent;
import com.benchmark.recommendation.repository.SongAffinityRepository;
import com.benchmark.recommendation.repository.UserSongInteractionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private static final Set<String> SUPPORTED_EVENTS = Set.of("play.started", "play.ended", "play.skipped");
    private static final Set<String> POSITIVE_EVENTS = Set.of("play.started", "play.ended");

    private final UserSongInteractionRepository interactionRepository;
    private final SongAffinityRepository affinityRepository;
    private final RecommendationCache recommendationCache;
    private final RecommendationProperties properties;
    private final Clock clock;

    public RecommendationService(
            UserSongInteractionRepository interactionRepository,
            SongAffinityRepository affinityRepository,
            RecommendationCache recommendationCache,
            RecommendationProperties properties,
            Clock clock) {
        this.interactionRepository = interactionRepository;
        this.affinityRepository = affinityRepository;
        this.recommendationCache = recommendationCache;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse dailyMix(UUID userId, Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        if (requestedLimit == null) {
            return recommendationCache.getDailyMix(userId).orElseGet(() -> {
                RecommendationResponse response = buildDailyMix(userId, limit);
                recommendationCache.putDailyMix(userId, response);
                return response;
            });
        }
        return buildDailyMix(userId, limit);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse similar(String songId, Integer requestedLimit) {
        String normalizedSongId = normalizeSongId(songId);
        int limit = normalizeLimit(requestedLimit);
        if (requestedLimit == null) {
            return recommendationCache.getSimilar(normalizedSongId).orElseGet(() -> {
                RecommendationResponse response = buildSimilar(normalizedSongId, limit);
                recommendationCache.putSimilar(normalizedSongId, response);
                return response;
            });
        }
        return buildSimilar(normalizedSongId, limit);
    }

    @Transactional
    public void recordPlaybackEvent(PlaybackEvent event) {
        ValidatedPlaybackEvent validated = validatePlaybackEvent(event);
        if (interactionRepository.existsByEventId(validated.eventId())) {
            return;
        }

        boolean positive = POSITIVE_EVENTS.contains(validated.eventType());
        interactionRepository.save(new UserSongInteraction(
                validated.eventId(),
                validated.eventType(),
                validated.userId().toString(),
                validated.songId(),
                validated.occurredAt(),
                positive));

        recommendationCache.evictDailyMix(validated.userId());
        recommendationCache.evictSimilar(validated.songId());

        if (positive) {
            List<String> relatedSongs = interactionRepository.findOtherPositiveSongsForUser(
                    validated.userId().toString(),
                    validated.songId(),
                    properties.maxLimit());
            Instant now = Instant.now(clock);
            for (String relatedSong : relatedSongs) {
                incrementBidirectionalAffinity(validated.songId(), relatedSong, now);
                recommendationCache.evictSimilar(relatedSong);
            }
        }
    }

    private RecommendationResponse buildDailyMix(UUID userId, int limit) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>(
                interactionRepository.findTopPositiveSongsForUser(userId.toString(), limit));
        if (ordered.size() < limit) {
            ordered.addAll(interactionRepository.findGlobalPositiveSongs(limit));
        }
        return response("daily-mix", new ArrayList<>(ordered), limit, "based on playback history");
    }

    private RecommendationResponse buildSimilar(String songId, int limit) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>(affinityRepository.findRelatedSongIds(songId, limit));
        ordered.remove(songId);
        if (ordered.size() < limit) {
            interactionRepository.findGlobalPositiveSongs(limit).stream()
                    .filter(candidate -> !candidate.equals(songId))
                    .forEach(ordered::add);
        }
        return response("similar", new ArrayList<>(ordered), limit, "users also played");
    }

    private RecommendationResponse response(String type, List<String> songs, int limit, String reason) {
        List<RecommendationItemResponse> items = new ArrayList<>();
        for (int index = 0; index < songs.size() && items.size() < limit; index++) {
            items.add(new RecommendationItemResponse(songs.get(index), items.size() + 1, reason));
        }
        return new RecommendationResponse(type, items);
    }

    private void incrementBidirectionalAffinity(String songId, String relatedSongId, Instant updatedAt) {
        if (songId.equals(relatedSongId)) {
            return;
        }
        affinityRepository.incrementAffinity(songId, relatedSongId, updatedAt);
        affinityRepository.incrementAffinity(relatedSongId, songId, updatedAt);
    }

    private int normalizeLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? properties.defaultLimit() : requestedLimit;
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new RecommendationValidationException(
                    "limit must be between 1 and " + properties.maxLimit());
        }
        return limit;
    }

    private String normalizeSongId(String songId) {
        if (songId == null || songId.isBlank()) {
            throw new RecommendationValidationException("songId is required");
        }
        return songId.trim();
    }

    private ValidatedPlaybackEvent validatePlaybackEvent(PlaybackEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("playback event is required");
        }
        UUID eventId = parseUuid(event.eventId(), "eventId");
        UUID userId = parseUuid(event.userId(), "userId");
        String eventType = required(event.type(), "type");
        String songId = required(event.songId(), "songId");
        if (!SUPPORTED_EVENTS.contains(eventType)) {
            throw new IllegalArgumentException("unsupported type: " + eventType);
        }
        if (event.timestamp() == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        return new ValidatedPlaybackEvent(eventId, eventType, userId, songId, event.timestamp());
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(required(value, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private record ValidatedPlaybackEvent(
            UUID eventId,
            String eventType,
            UUID userId,
            String songId,
            Instant occurredAt) {
    }
}
