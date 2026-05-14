package com.musicstreaming.recommendation.controller;

import com.musicstreaming.recommendation.dto.DailyMixResponse;
import com.musicstreaming.recommendation.dto.SimilarSongsResponse;
import com.musicstreaming.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommend")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/daily-mix")
    public ResponseEntity<DailyMixResponse> getDailyMix(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(recommendationService.getDailyMix(userId));
    }

    @GetMapping("/similar/{songId}")
    public ResponseEntity<SimilarSongsResponse> getSimilarSongs(@PathVariable Long songId) {
        return ResponseEntity.ok(recommendationService.getSimilarSongs(songId));
    }
}
