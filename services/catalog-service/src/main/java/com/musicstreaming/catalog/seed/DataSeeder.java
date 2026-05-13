package com.musicstreaming.catalog.seed;

import com.musicstreaming.catalog.repository.SongRepository;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String INSERT_SQL = """
            INSERT INTO songs (track_id, title, artist, artist_id, album, release_date, year,
                               genre, duration_ms, popularity, danceability, energy, musical_key,
                               loudness, mode, instrumentalness, tempo, stream_count,
                               country, explicit, label)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (track_id) DO NOTHING
            """;

    @Value("${catalog.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${catalog.seed.batch-size:500}")
    private int batchSize;

    private final SongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(SongRepository songRepository, JdbcTemplate jdbcTemplate) {
        this.songRepository = songRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!seedEnabled) {
            log.info("Catalog seed disabled, skipping");
            return;
        }
        if (songRepository.count() > 0) {
            log.info("Catalog already seeded, skipping");
            return;
        }

        log.info("Starting catalog seed from classpath:data/songs.csv ...");
        ClassPathResource resource = new ClassPathResource("data/songs.csv");

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            List<Object[]> batch = new ArrayList<>(batchSize);
            int batchNum = 0;
            int skipped = 0;

            for (CSVRecord record : parser) {
                Object[] args2 = toArgs(record);
                if (args2 == null) {
                    skipped++;
                    continue;
                }
                batch.add(args2);
                if (batch.size() >= batchSize) {
                    jdbcTemplate.batchUpdate(INSERT_SQL, batch);
                    batchNum++;
                    log.debug("Committed batch {}", batchNum);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(INSERT_SQL, batch);
                batchNum++;
            }
            log.info("Seed complete: {} batches committed, {} rows skipped", batchNum, skipped);
        }
    }

    private Object[] toArgs(CSVRecord r) {
        try {
            String trackId        = r.get("track_id");
            String title          = r.get("track_name");
            String artist         = r.get("artist_name");

            if (title.isBlank() || artist.isBlank()) {
                log.warn("Skipping row {}: blank title or artist", r.getRecordNumber());
                return null;
            }

            String artistId       = computeArtistId(artist);
            String album          = r.get("album_name");
            LocalDate releaseDate = LocalDate.parse(r.get("release_date"));
            int year              = releaseDate.getYear();
            String genre          = r.get("genre");
            int durationMs        = Integer.parseInt(r.get("duration_ms"));
            int popularity        = Integer.parseInt(r.get("popularity"));
            double danceability   = Double.parseDouble(r.get("danceability"));
            double energy         = Double.parseDouble(r.get("energy"));
            int musicalKey        = Integer.parseInt(r.get("key"));
            double loudness       = Double.parseDouble(r.get("loudness"));
            int mode              = Integer.parseInt(r.get("mode"));
            double instrumentalness = Double.parseDouble(r.get("instrumentalness"));
            double tempo          = Double.parseDouble(r.get("tempo"));
            long streamCount      = Long.parseLong(r.get("stream_count"));
            String country        = r.get("country");
            boolean explicit      = "1".equals(r.get("explicit").trim());
            String label          = r.get("label");

            return new Object[]{
                trackId, title, artist, artistId, album, releaseDate, year, genre,
                durationMs, popularity, danceability, energy, musicalKey, loudness, mode,
                instrumentalness, tempo, streamCount, country, explicit, label
            };
        } catch (Exception e) {
            log.warn("Skipping malformed row {}: {}", r.getRecordNumber(), e.getMessage());
            return null;
        }
    }

    public static String computeArtistId(String artistName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(artistName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
