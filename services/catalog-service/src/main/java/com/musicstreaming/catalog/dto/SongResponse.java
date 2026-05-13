package com.musicstreaming.catalog.dto;

import java.time.LocalDate;

public record SongResponse(
        Long id,
        String trackId,
        String title,
        String artist,
        String artistId,
        String album,
        LocalDate releaseDate,
        Integer year,
        String genre,
        Integer durationMs,
        Integer popularity,
        Double danceability,
        Double energy,
        Integer musicalKey,
        Double loudness,
        Integer mode,
        Double instrumentalness,
        Double tempo,
        Long streamCount,
        String country,
        Boolean explicit,
        String label
) {}
