package com.benchmark.catalog.ingestion;

import com.benchmark.catalog.entity.Song;
import com.benchmark.catalog.repository.SongRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogDatasetIngestionRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogDatasetIngestionRunner.class);
    private static final int BATCH_SIZE = 500;

    private final CatalogDatasetProperties properties;
    private final CatalogCsvReader csvReader;
    private final SongRepository songRepository;
    private final EntityManager entityManager;

    public CatalogDatasetIngestionRunner(
            CatalogDatasetProperties properties,
            CatalogCsvReader csvReader,
            SongRepository songRepository,
            EntityManager entityManager
    ) {
        this.properties = properties;
        this.csvReader = csvReader;
        this.songRepository = songRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.ingestionEnabled()) {
            LOGGER.info("Catalog dataset ingestion disabled");
            return;
        }
        Path datasetPath = Path.of(properties.datasetPath());
        List<Song> batch = new ArrayList<>(BATCH_SIZE);
        AtomicInteger read = new AtomicInteger();
        AtomicInteger inserted = new AtomicInteger();
        csvReader.forEachSong(datasetPath, song -> {
            read.incrementAndGet();
            batch.add(song);
            if (batch.size() >= BATCH_SIZE) {
                inserted.addAndGet(persistNewSongs(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) {
            inserted.addAndGet(persistNewSongs(batch));
        }
        if (read.get() == 0) {
            throw new IllegalStateException("Catalog dataset is empty: " + datasetPath);
        }
        LOGGER.info("Catalog dataset ingestion completed from {}: {} rows read, {} inserted, {} total songs",
                datasetPath, read.get(), inserted.get(), songRepository.count());
    }

    @Transactional
    int persistNewSongs(List<Song> songs) {
        Set<String> existingIds = songRepository.findExistingIds(songs.stream().map(Song::getId).toList());
        List<Song> newSongs = songs.stream()
                .filter(song -> !existingIds.contains(song.getId()))
                .toList();
        songRepository.saveAll(newSongs);
        songRepository.flush();
        entityManager.clear();
        return newSongs.size();
    }
}
