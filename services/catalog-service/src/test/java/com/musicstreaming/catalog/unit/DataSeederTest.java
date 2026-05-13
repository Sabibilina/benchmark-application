package com.musicstreaming.catalog.unit;

import com.musicstreaming.catalog.repository.SongRepository;
import com.musicstreaming.catalog.seed.DataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataSeeder, "seedEnabled", true);
        ReflectionTestUtils.setField(dataSeeder, "batchSize", 500);
    }

    @Test
    void seed_whenDisabled_nothingIsExecuted() throws Exception {
        ReflectionTestUtils.setField(dataSeeder, "seedEnabled", false);

        dataSeeder.run(new DefaultApplicationArguments());

        verify(songRepository, never()).count();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void seed_whenTableAlreadyHasData_skipsInsert() throws Exception {
        when(songRepository.count()).thenReturn(1000L);

        dataSeeder.run(new DefaultApplicationArguments());

        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void computeArtistId_sameInput_returnsSameHash() {
        String id1 = DataSeeder.computeArtistId("The Beatles");
        String id2 = DataSeeder.computeArtistId("The Beatles");

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void computeArtistId_isCaseInsensitive() {
        String lower = DataSeeder.computeArtistId("the beatles");
        String upper = DataSeeder.computeArtistId("THE BEATLES");
        String mixed = DataSeeder.computeArtistId("The Beatles");

        assertThat(lower).isEqualTo(upper).isEqualTo(mixed);
    }

    @Test
    void computeArtistId_returns16HexChars() {
        String id = DataSeeder.computeArtistId("Test Artist");

        assertThat(id).hasSize(16).matches("[0-9a-f]+");
    }

    @Test
    void computeArtistId_differentArtists_returnDifferentIds() {
        String id1 = DataSeeder.computeArtistId("Artist A");
        String id2 = DataSeeder.computeArtistId("Artist B");

        assertThat(id1).isNotEqualTo(id2);
    }
}
