package com.benchmark.analytics.controller;

import com.benchmark.analytics.dto.GlobalChartItemResponse;
import com.benchmark.analytics.dto.HistoryPageResponse;
import com.benchmark.analytics.security.AuthenticatedUser;
import com.benchmark.analytics.security.UserPrincipalResolver;
import com.benchmark.analytics.service.AnalyticsService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserPrincipalResolver userPrincipalResolver;

    public AnalyticsController(AnalyticsService analyticsService, UserPrincipalResolver userPrincipalResolver) {
        this.analyticsService = analyticsService;
        this.userPrincipalResolver = userPrincipalResolver;
    }

    @GetMapping("/analytics/me/history")
    HistoryPageResponse history(Authentication authentication,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size) {
        AuthenticatedUser user = userPrincipalResolver.resolve(authentication);
        return analyticsService.history(user.userId(), page, size);
    }

    @GetMapping("/analytics/charts/global")
    List<GlobalChartItemResponse> globalChart(@RequestParam(required = false) Integer limit) {
        return analyticsService.globalChart(limit);
    }
}
