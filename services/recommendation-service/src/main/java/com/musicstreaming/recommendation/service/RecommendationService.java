package com.musicstreaming.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.recommendation.dto.DailyMixResponse;
import com.musicstreaming.recommendation.dto.SimilarSongsResponse;
import com.musicstreaming.recommendation.dto.SongRecommendation;
import com.musicstreaming.recommendation.model.RecommendationSong;
import com.musicstreaming.recommendation.repository.PlayEventRepository;
import com.musicstreaming.recommendation.repository.RecommendationSongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    public static final double TEMPO_TOLERANCE = 20.0;
    public static final int HISTORY_LOOKBACK_DAYS = 30;
    public static final int HISTORY_TOP_N = 10;

    private final RecommendationSongRepository songRepo;
    private final PlayEventRepository playEventRepo;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int dailyMixSize;
    private final int similarSize;
    private final long cacheTtlSeconds;

    public RecommendationService(
            RecommendationSongRepository songRepo,
            PlayEventRepository playEventRepo,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${recommendation.daily-mix-size:20}") int dailyMixSize,
            @Value("${recommendation.similar-size:10}") int similarSize,
            @Value("${recommendation.cache.ttl-seconds:3600}") long cacheTtlSeconds) {
        this.songRepo = songRepo;
        this.playEventRepo = playEventRepo;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.dailyMixSize = dailyMixSize;
        this.similarSize = similarSize;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public DailyMixResponse getDailyMix(String userId) {
        String cacheKey = "daily-mix:" + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, DailyMixResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for {}: {}", cacheKey, e.getMessage());
        }

        DailyMixResponse response = computeDailyMix(userId);

        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", cacheKey, e.getMessage());
        }

        return response;
    }

    public SimilarSongsResponse getSimilarSongs(Long songId) {
        String cacheKey = "similar:" + songId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, SimilarSongsResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for {}: {}", cacheKey, e.getMessage());
        }

        SimilarSongsResponse response = computeSimilarSongs(songId);

        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", cacheKey, e.getMessage());
        }

        return response;
    }

    private DailyMixResponse computeDailyMix(String userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(HISTORY_LOOKBACK_DAYS);
        List<String> topSongIdStrings = playEventRepo.findTopSongIdsByUser(userId, since, HISTORY_TOP_N);
        List<Long> topSongIds = parseLongs(topSongIdStrings);

        String preferredGenre = null;
        if (!topSongIds.isEmpty()) {
            preferredGenre = findMostCommonGenre(topSongIds);
        }

        if (preferredGenre == null) {
            List<String> globalTopStrings = playEventRepo.findGlobalTopSongIds(HISTORY_TOP_N);
            List<Long> globalTopIds = parseLongs(globalTopStrings);
            if (!globalTopIds.isEmpty()) {
                preferredGenre = findMostCommonGenre(globalTopIds);
            }
        }

        List<RecommendationSong> songs;
        if (preferredGenre != null) {
            List<Long> excludeIds = topSongIds.isEmpty() ? List.of(-1L) : topSongIds;
            songs = songRepo.findByGenreExcluding(preferredGenre, excludeIds, PageRequest.of(0, dailyMixSize));
        } else {
            songs = songRepo.findRandom(PageRequest.of(0, dailyMixSize));
        }

        if (songs.isEmpty()) {
            songs = songRepo.findRandom(PageRequest.of(0, dailyMixSize));
        }

        return new DailyMixResponse(songs.stream().map(SongRecommendation::from).toList());
    }

    private SimilarSongsResponse computeSimilarSongs(Long songId) {
        Optional<RecommendationSong> targetOpt = songRepo.findById(songId);

        List<RecommendationSong> songs;
        if (targetOpt.isPresent()) {
            RecommendationSong target = targetOpt.get();
            songs = new ArrayList<>(songRepo.findSimilar(
                    target.getGenre(), target.getTempo(), TEMPO_TOLERANCE, songId,
                    PageRequest.of(0, similarSize)));

            if (songs.size() < similarSize) {
                int needed = similarSize - songs.size();
                List<Long> existingIds = new ArrayList<>(songs.stream().map(RecommendationSong::getId).toList());
                existingIds.add(songId);
                List<RecommendationSong> fill = songRepo.findByGenreExcluding(
                        target.getGenre(), existingIds, PageRequest.of(0, needed));
                songs.addAll(fill);
            }
        } else {
            songs = songRepo.findRandom(PageRequest.of(0, similarSize));
        }

        if (songs.isEmpty()) {
            songs = songRepo.findRandom(PageRequest.of(0, similarSize));
        }

        return new SimilarSongsResponse(songId, songs.stream().map(SongRecommendation::from).toList());
    }

    private String findMostCommonGenre(List<Long> songIds) {
        List<String> genres = songRepo.findGenresByIds(songIds);
        return genres.stream()
                .filter(g -> g != null && !g.isBlank())
                .collect(Collectors.groupingBy(g -> g, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<Long> parseLongs(List<String> strings) {
        List<Long> result = new ArrayList<>();
        for (String s : strings) {
            try {
                result.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                log.debug("Skipping non-numeric songId in play events: {}", s);
            }
        }
        return result;
    }
}
