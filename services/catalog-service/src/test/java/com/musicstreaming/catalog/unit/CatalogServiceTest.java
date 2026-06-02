package com.musicstreaming.catalog.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicstreaming.catalog.dto.PagedResponse;
import com.musicstreaming.catalog.dto.SongResponse;
import com.musicstreaming.catalog.entity.Song;
import com.musicstreaming.catalog.exception.SongNotFoundException;
import com.musicstreaming.catalog.repository.SongRepository;
import com.musicstreaming.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// Redis stubs in setUp() are not needed by findById or deep-page tests.
// LENIENT avoids UnnecessaryStubbingException for those tests while keeping
// strict mode for the stubs that are declared inside individual tests.
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogServiceTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    // Real ObjectMapper — avoids generic-type complications when mocking
    // readValue(String, TypeReference<T>) and keeps the test honest about
    // serialisation round-trips.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Default: cache miss → fall through to the DB
        when(valueOps.get(anyString())).thenReturn(null);
        catalogService = new CatalogService(songRepository, redisTemplate, objectMapper, 300L);
    }

    @Test
    void findAll_returnsPagedResponse() {
        Song song = buildSong(1L, "Test Song", "Test Artist");
        Page<Song> page = new PageImpl<>(List.of(song), PageRequest.of(0, 20), 1);
        when(songRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<SongResponse> result = catalogService.findAll(0, 20, "id", "asc");

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.content().get(0).title()).isEqualTo("Test Song");
        assertThat(result.content().get(0).artist()).isEqualTo("Test Artist");
    }

    @Test
    void findAll_emptyPage_returnsEmptyContent() {
        when(songRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        PagedResponse<SongResponse> result = catalogService.findAll(0, 20, "id", "asc");

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void findAll_sizeExceedsMax_isCappedAt100() {
        when(songRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        catalogService.findAll(0, 500, "id", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(songRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void findAll_invalidSortField_fallsBackToId() {
        when(songRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        catalogService.findAll(0, 20, "nonexistent_field", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(songRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void findAll_descDirection_usedInPageable() {
        when(songRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        catalogService.findAll(0, 20, "popularity", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(songRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("popularity").isDescending()).isTrue();
    }

    @Test
    void findAll_deepPage_bypassesCache() {
        // Pages >= CACHE_PAGE_DEPTH (5) must skip the Redis lookup and go straight to DB.
        when(songRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        catalogService.findAll(5, 20, "id", "asc");

        verify(songRepository).findAll(any(Pageable.class));
        // opsForValue() must never be called for a deep page
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void findAll_cacheHit_returnsWithoutDbCall() throws Exception {
        // Serialise an empty PagedResponse so the real ObjectMapper can round-trip it.
        PagedResponse<SongResponse> expected =
                new PagedResponse<>(List.of(), 0, 20, 0L, 0, true);
        String json = objectMapper.writeValueAsString(expected);

        when(valueOps.get(anyString())).thenReturn(json);

        PagedResponse<SongResponse> result = catalogService.findAll(0, 20, "id", "asc");

        assertThat(result).isNotNull();
        // DB must NOT have been called on a cache hit
        verify(songRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findById_existingSong_returnsMappedDto() {
        Song song = buildSong(1L, "Test Song", "Test Artist");
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        SongResponse result = catalogService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Test Song");
        assertThat(result.artist()).isEqualTo("Test Artist");
        assertThat(result.genre()).isEqualTo("Pop");
        assertThat(result.tempo()).isEqualTo(120.0);
    }

    @Test
    void findById_missingSong_throwsSongNotFoundException() {
        when(songRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.findById(99L))
                .isInstanceOf(SongNotFoundException.class)
                .hasMessageContaining("99");
    }

    private Song buildSong(Long id, String title, String artist) {
        Song song = new Song();
        song.setId(id);
        song.setTrackId("TRK-TEST000001");
        song.setTitle(title);
        song.setArtist(artist);
        song.setArtistId("deadbeef00000000");
        song.setAlbum("Test Album");
        song.setReleaseDate(LocalDate.of(2020, 6, 15));
        song.setYear(2020);
        song.setGenre("Pop");
        song.setDurationMs(210000);
        song.setPopularity(75);
        song.setDanceability(0.8);
        song.setEnergy(0.7);
        song.setMusicalKey(5);
        song.setLoudness(-5.5);
        song.setMode(1);
        song.setInstrumentalness(0.0);
        song.setTempo(120.0);
        song.setStreamCount(50000L);
        song.setCountry("US");
        song.setExplicit(false);
        song.setLabel("Test Label");
        return song;
    }
}
