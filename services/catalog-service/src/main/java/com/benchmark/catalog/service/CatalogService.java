package com.benchmark.catalog.service;

import com.benchmark.catalog.dto.SongDetailResponse;
import com.benchmark.catalog.dto.SongListItemResponse;
import com.benchmark.catalog.dto.SongPageResponse;
import com.benchmark.catalog.entity.Song;
import com.benchmark.catalog.ingestion.CatalogDatasetProperties;
import com.benchmark.catalog.repository.SongRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final SongRepository songRepository;
    private final CatalogDatasetProperties properties;

    public CatalogService(SongRepository songRepository, CatalogDatasetProperties properties) {
        this.songRepository = songRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SongPageResponse listSongs(int page, Integer requestedSize) {
        int configuredSize = requestedSize == null ? properties.defaultPageSize() : requestedSize;
        int boundedSize = Math.min(configuredSize, properties.maxPageSize());
        var songs = songRepository.findAll(PageRequest.of(page, boundedSize, Sort.by("title").ascending()));
        List<SongListItemResponse> content = songs.stream().map(this::toListItem).toList();
        return new SongPageResponse(content, songs.getNumber(), songs.getSize(), songs.getTotalElements(), songs.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SongDetailResponse getSong(String id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        return toDetail(song);
    }

    private SongListItemResponse toListItem(Song song) {
        return new SongListItemResponse(
                song.getId(),
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getGenre(),
                song.getBpm(),
                song.getReleaseYear(),
                song.getReleaseDate(),
                song.getPopularity(),
                song.getDurationMs(),
                song.getMetadata()
        );
    }

    private SongDetailResponse toDetail(Song song) {
        return new SongDetailResponse(
                song.getId(),
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getGenre(),
                song.getBpm(),
                song.getReleaseYear(),
                song.getReleaseDate(),
                song.getPopularity(),
                song.getDurationMs(),
                song.getMetadata(),
                song.getCreatedAt(),
                song.getUpdatedAt()
        );
    }
}
