package com.benchmark.catalog.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.benchmark.catalog.repository.SongRepository;
import com.benchmark.catalog.support.TestDataFiles;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

class CatalogDatasetIngestionRunnerTest {

    @Test
    void csvReaderMapsRepresentativeDatasetRows() {
        var songs = new CatalogCsvReader().read(TestDataFiles.catalogCsv());

        assertThat(songs).hasSize(2);
        assertThat(songs.getFirst().getId()).isEqualTo("test-song-1");
        assertThat(songs.getFirst().getTitle()).isEqualTo("First Test Song");
        assertThat(songs.getFirst().getArtist()).isEqualTo("Test Artist");
        assertThat(songs.getFirst().getGenre()).isEqualTo("pop");
        assertThat(songs.getFirst().getReleaseYear()).isEqualTo(2020);
        assertThat(songs.getFirst().getMetadata()).containsEntry("track_name", "First Test Song");
    }

    @Test
    void ingestionSkipsExistingSongs() {
        SongRepository repository = Mockito.mock(SongRepository.class);
        EntityManager entityManager = Mockito.mock(EntityManager.class);
        Mockito.when(repository.findExistingIds(Mockito.anyCollection())).thenReturn(Set.of("test-song-1"));
        var properties = new CatalogDatasetProperties(TestDataFiles.catalogCsv().toString(), true, 20, 100);
        var runner = new CatalogDatasetIngestionRunner(properties, new CatalogCsvReader(), repository, entityManager);

        runner.run(new DefaultApplicationArguments());

        Mockito.verify(repository).saveAll(Mockito.argThat(songs ->
                songs.iterator().next().getId().equals("test-song-2")
        ));
        Mockito.verify(repository).flush();
        Mockito.verify(entityManager).clear();
    }
}
