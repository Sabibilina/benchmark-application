package com.musicstreaming.recommendation.seed;

import com.musicstreaming.recommendation.repository.RecommendationSongRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecommendationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendationSeeder.class);

    private static final String INSERT_SQL =
            "INSERT INTO recommendation_songs (track_id, title, artist, genre, tempo, year) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (track_id) DO NOTHING";

    @Value("${recommendation.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${recommendation.seed.batch-size:500}")
    private int batchSize;

    private final RecommendationSongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;

    public RecommendationSeeder(RecommendationSongRepository songRepository, JdbcTemplate jdbcTemplate) {
        this.songRepository = songRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!seedEnabled) {
            log.info("Recommendation seed disabled, skipping");
            return;
        }
        if (songRepository.count() > 0) {
            log.info("recommendation_songs already populated, skipping seed");
            return;
        }

        log.info("Seeding recommendation_songs from CSV…");
        ClassPathResource resource = new ClassPathResource("data/songs.csv");

        long count = 0;
        List<Object[]> batch = new ArrayList<>(batchSize);

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord row : parser) {
                String trackId = row.get("track_id");
                String title   = row.get("track_name");
                String artist  = row.get("artist_name");
                String genre   = row.get("genre");
                Double tempo   = parseDouble(row.get("tempo"));
                Integer year   = extractYear(row.get("release_date"));

                batch.add(new Object[]{trackId, title, artist, genre, tempo, year});

                if (batch.size() >= batchSize) {
                    jdbcTemplate.batchUpdate(INSERT_SQL, batch);
                    count += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(INSERT_SQL, batch);
                count += batch.size();
            }
        }

        log.info("Seeded {} songs into recommendation_songs", count);
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer extractYear(String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) return null;
        try {
            return LocalDate.parse(releaseDate).getYear();
        } catch (Exception e) {
            try {
                return Integer.parseInt(releaseDate.substring(0, 4));
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
