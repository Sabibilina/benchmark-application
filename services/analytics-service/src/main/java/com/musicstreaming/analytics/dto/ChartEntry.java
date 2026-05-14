package com.musicstreaming.analytics.dto;

public record ChartEntry(
        int rank,
        String songId,
        long playCount
) {}
