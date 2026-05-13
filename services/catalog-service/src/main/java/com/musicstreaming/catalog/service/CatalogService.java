package com.musicstreaming.catalog.service;

import com.musicstreaming.catalog.dto.PagedResponse;
import com.musicstreaming.catalog.dto.SongResponse;
import com.musicstreaming.catalog.entity.Song;
import com.musicstreaming.catalog.exception.SongNotFoundException;
import com.musicstreaming.catalog.repository.SongRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "artist", "year", "popularity", "tempo");

    private final SongRepository songRepository;

    public CatalogService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SongResponse> findAll(int page, int size, String sort, String direction) {
        size = Math.min(size, MAX_PAGE_SIZE);
        if (!ALLOWED_SORT_FIELDS.contains(sort)) {
            sort = "id";
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));
        Page<Song> songs = songRepository.findAll(pageable);
        return PagedResponse.of(songs.map(this::toResponse));
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
