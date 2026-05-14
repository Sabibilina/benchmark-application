package com.musicstreaming.search.dto;

public record SearchRequest(
        String q,
        String genre,
        Double bpmMin,
        Double bpmMax,
        Integer year
) {}
