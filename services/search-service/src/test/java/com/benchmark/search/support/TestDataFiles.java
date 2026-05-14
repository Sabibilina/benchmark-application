package com.benchmark.search.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestDataFiles {

    private TestDataFiles() {
    }

    public static Path catalogCsv() {
        try {
            Path file = Files.createTempFile("search-catalog", ".csv");
            Files.writeString(file, """
                    track_id,track_name,artist_name,album_name,track_genre,tempo,release_year
                    song-1,Ocean Drive,Duke Dumont,Blase Boys Club,dance,115.5,2015
                    song-2,Quiet Room,The Readers,Paper Trails,indie,92,2020
                    """);
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create test catalog CSV", ex);
        }
    }
}
