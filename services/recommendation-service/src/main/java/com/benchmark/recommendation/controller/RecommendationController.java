package com.benchmark.recommendation.controller;

import com.benchmark.recommendation.dto.RecommendationResponse;
import com.benchmark.recommendation.security.AuthenticatedUser;
import com.benchmark.recommendation.security.UserPrincipalResolver;
import com.benchmark.recommendation.service.RecommendationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommend")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserPrincipalResolver userPrincipalResolver;

    public RecommendationController(
            RecommendationService recommendationService,
            UserPrincipalResolver userPrincipalResolver) {
        this.recommendationService = recommendationService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GetMapping("/daily-mix")
    RecommendationResponse dailyMix(Authentication authentication, @RequestParam(required = false) Integer limit) {
        AuthenticatedUser user = userPrincipalResolver.resolve(authentication);
        return recommendationService.dailyMix(user.id(), limit);
    }

    @GetMapping("/similar/{songId}")
    RecommendationResponse similar(@PathVariable String songId, @RequestParam(required = false) Integer limit) {
        return recommendationService.similar(songId, limit);
    }
}
