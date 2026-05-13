package com.benchmark.catalog.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestDataFiles {

    private TestDataFiles() {
    }

    public static Path catalogCsv() {
        try {
            Path file = Files.createTempFile("catalog-test-", ".csv");
            Files.writeString(file, """
                    track_id,track_name,artist_name,album_name,track_genre,tempo,release_date,popularity,duration_ms
                    test-song-1,First Test Song,Test Artist,Test Album,pop,120.5,2020-01-02,71,210000
                    test-song-2,Second Test Song,Another Artist,Second Album,rock,98.0,2021-03-04,64,190000
                    """);
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test catalog CSV", ex);
        }
    }
}
