package com.musicstreaming.recommendation.dto;

import java.util.List;

public record SimilarSongsResponse(Long seedSongId, List<SongRecommendation> songs) {}
