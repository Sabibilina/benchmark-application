package com.benchmark.catalog.ingestion;

import com.benchmark.catalog.entity.Song;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class CatalogCsvReader {

    private static final List<String> ID_KEYS = List.of("track_id", "id", "spotify_id", "song_id");
    private static final List<String> TITLE_KEYS = List.of("track_name", "song", "song_name", "title", "name");
    private static final List<String> ARTIST_KEYS = List.of("artist_name", "artist", "artists", "singer");
    private static final List<String> ALBUM_KEYS = List.of("album_name", "album");
    private static final List<String> GENRE_KEYS = List.of("track_genre", "genre", "playlist_genre");
    private static final List<String> BPM_KEYS = List.of("tempo", "bpm");
    private static final List<String> RELEASE_DATE_KEYS = List.of("release_date", "album_release_date", "date");
    private static final List<String> RELEASE_YEAR_KEYS = List.of("release_year", "year");
    private static final List<String> POPULARITY_KEYS = List.of("popularity", "track_popularity");
    private static final List<String> DURATION_KEYS = List.of("duration_ms", "duration");

    public List<Song> read(Path path) {
        List<Song> songs = new ArrayList<>();
        forEachSong(path, songs::add);
        if (songs.isEmpty()) {
            throw new IllegalStateException("Catalog dataset is empty: " + path);
        }
        return songs;
    }

    public void forEachSong(Path path, Consumer<Song> consumer) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Catalog dataset file does not exist: " + path);
        }
        try (Reader reader = Files.newBufferedReader(path);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                consumer.accept(toSong(record));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read catalog dataset from " + path, ex);
        }
    }

    private Song toSong(CSVRecord record) {
        Map<String, String> values = normalizedValues(record);
        String title = first(values, TITLE_KEYS)
                .orElseThrow(() -> missing(record, "title"));
        String artist = first(values, ARTIST_KEYS)
                .orElseThrow(() -> missing(record, "artist"));
        String id = first(values, ID_KEYS)
                .orElseGet(() -> stableId(title, artist));
        Song song = new Song(id, title, artist);
        first(values, ALBUM_KEYS).ifPresent(song::setAlbum);
        first(values, GENRE_KEYS).ifPresent(song::setGenre);
        first(values, BPM_KEYS).flatMap(this::parseDecimal).ifPresent(song::setBpm);
        first(values, RELEASE_DATE_KEYS).flatMap(this::parseDate).ifPresent(song::setReleaseDate);
        Optional<Integer> releaseYear = first(values, RELEASE_YEAR_KEYS).flatMap(this::parseInteger);
        if (releaseYear.isEmpty() && song.getReleaseDate() != null) {
            releaseYear = Optional.of(song.getReleaseDate().getYear());
        }
        releaseYear.ifPresent(song::setReleaseYear);
        first(values, POPULARITY_KEYS).flatMap(this::parseInteger).ifPresent(song::setPopularity);
        first(values, DURATION_KEYS).flatMap(this::parseInteger).ifPresent(song::setDurationMs);
        song.setMetadata(values);
        return song;
    }

    private Map<String, String> normalizedValues(CSVRecord record) {
        Map<String, String> values = new LinkedHashMap<>();
        record.toMap().forEach((key, value) -> {
            if (key != null && value != null && !value.isBlank()) {
                values.put(normalizeKey(key), value.trim());
            }
        });
        return values;
    }

    private Optional<String> first(Map<String, String> values, List<String> keys) {
        return keys.stream()
                .map(values::get)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private Optional<BigDecimal> parseDecimal(String value) {
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parseInteger(String value) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> parseDate(String value) {
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/yyyy"))) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // Try next known dataset date shape.
            }
        }
        try {
            return Optional.of(Year.parse(value).atDay(1));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private IllegalStateException missing(CSVRecord record, String field) {
        return new IllegalStateException("Catalog dataset row " + record.getRecordNumber() + " is missing " + field);
    }

    private String stableId(String title, String artist) {
        return normalizeKey(artist + "-" + title).replace('_', '-');
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
