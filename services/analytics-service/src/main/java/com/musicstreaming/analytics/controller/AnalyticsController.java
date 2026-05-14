package com.musicstreaming.analytics.controller;

import com.musicstreaming.analytics.dto.ChartEntry;
import com.musicstreaming.analytics.dto.HistoryEntry;
import com.musicstreaming.analytics.service.AnalyticsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/me/history")
    public List<HistoryEntry> getHistory(Authentication authentication) {
        return analyticsService.getHistory(authentication.getName());
    }

    @GetMapping("/charts/global")
    public List<ChartEntry> getGlobalCharts() {
        return analyticsService.getGlobalCharts();
    }
}
