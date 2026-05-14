package com.musicstreaming.recommendation.dto;

import com.musicstreaming.recommendation.model.RecommendationSong;

public record SongRecommendation(
        Long id,
        String title,
        String artist,
        String genre,
        Double tempo
) {
    public static SongRecommendation from(RecommendationSong song) {
        return new SongRecommendation(
                song.getId(),
                song.getTitle(),
                song.getArtist(),
                song.getGenre(),
                song.getTempo()
        );
    }
}
