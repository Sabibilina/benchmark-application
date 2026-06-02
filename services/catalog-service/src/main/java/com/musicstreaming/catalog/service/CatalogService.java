package com.musicstreaming.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.catalog.dto.PagedResponse;
import com.musicstreaming.catalog.dto.SongResponse;
import com.musicstreaming.catalog.entity.Song;
import com.musicstreaming.catalog.exception.SongNotFoundException;
import com.musicstreaming.catalog.repository.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private static final int MAX_PAGE_SIZE = 100;
    // Cache the first CACHE_PAGE_DEPTH pages for each genre+sort+size combination.
    // These represent the "hot zone" accessed by the vast majority of browsing traffic.
    private static final int CACHE_PAGE_DEPTH = 5;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "artist", "year", "popularity", "tempo");

    private final SongRepository songRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long cacheTtlSeconds;

    public CatalogService(
            SongRepository songRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${catalog.cache.ttl-seconds:300}") long cacheTtlSeconds) {
        this.songRepository = songRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SongResponse> findAll(int page, int size, String sort, String direction) {
        size = Math.min(size, MAX_PAGE_SIZE);
        if (!ALLOWED_SORT_FIELDS.contains(sort)) {
            sort = "id";
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        if (page < CACHE_PAGE_DEPTH) {
            String cacheKey = "catalog:page:%d:size:%d:sort:%s:dir:%s".formatted(page, size, sort, direction);
            PagedResponse<SongResponse> cached = fromCache(cacheKey);
            if (cached != null) {
                return cached;
            }
            PagedResponse<SongResponse> result = fetchFromDb(page, size, sort, dir);
            toCache(cacheKey, result);
            return result;
        }

        return fetchFromDb(page, size, sort, dir);
    }

    @Transactional(readOnly = true)
    public SongResponse findById(Long id) {
        return songRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new SongNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<SongResponse> findTopTracksByArtistId(String artistId) {
        return songRepository.findTop10ByArtistIdOrderByPopularityDesc(artistId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PagedResponse<SongResponse> fetchFromDb(int page, int size, String sort, Sort.Direction dir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));
        Page<Song> songs = songRepository.findAll(pageable);
        return PagedResponse.of(songs.map(this::toResponse));
    }

    private PagedResponse<SongResponse> fromCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.debug("Cache read miss/error for key {}: {}", key, e.getMessage());
        }
        return null;
    }

    private void toCache(String key, PagedResponse<SongResponse> value) {
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.debug("Cache write error for key {}: {}", key, e.getMessage());
        }
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(
                song.getId(),
                song.getTrackId(),
                song.getTitle(),
                song.getArtist(),
                song.getArtistId(),
                song.getAlbum(),
                song.getReleaseDate(),
                song.getYear(),
                song.getGenre(),
                song.getDurationMs(),
                song.getPopularity(),
                song.getDanceability(),
                song.getEnergy(),
                song.getMusicalKey(),
                song.getLoudness(),
                song.getMode(),
                song.getInstrumentalness(),
                song.getTempo(),
                song.getStreamCount(),
                song.getCountry(),
                song.getExplicit(),
                song.getLabel()
        );
    }
}
