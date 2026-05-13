package com.benchmark.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.benchmark.catalog.entity.Song;
import com.benchmark.catalog.ingestion.CatalogDatasetProperties;
import com.benchmark.catalog.repository.SongRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class CatalogServiceTest {

    private final SongRepository repository = Mockito.mock(SongRepository.class);
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(repository, new CatalogDatasetProperties("/tmp/catalog.csv", true, 20, 50));
    }

    @Test
    void listSongsUsesConfiguredDefaultPageSizeWhenSizeIsMissing() {
        Song song = new Song("song-1", "Song One", "Artist One");
        when(repository.findAll(Mockito.any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            return new PageImpl<>(List.of(song), pageable, 1);
        });

        var response = catalogService.listSongs(0, null);

        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void listSongsReturnsPaginationMetadataAndItems() {
        Song song = new Song("song-1", "Song One", "Artist One");
        song.setGenre("pop");
        song.setMetadata(Map.of("track_genre", "pop"));
        when(repository.findAll(Mockito.any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            return new PageImpl<>(List.of(song), pageable, 1);
        });

        var response = catalogService.listSongs(0, 100);

        assertThat(response.size()).isEqualTo(50);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo("song-1");
    }

    @Test
    void getSongReturnsDetailsWhenPresent() {
        Song song = new Song("song-1", "Song One", "Artist One");
        when(repository.findById("song-1")).thenReturn(Optional.of(song));

        var response = catalogService.getSong("song-1");

        assertThat(response.id()).isEqualTo("song-1");
        assertThat(response.title()).isEqualTo("Song One");
    }

    @Test
    void getSongRejectsMissingSong() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.getSong("missing"))
                .isInstanceOf(SongNotFoundException.class);
    }
}
