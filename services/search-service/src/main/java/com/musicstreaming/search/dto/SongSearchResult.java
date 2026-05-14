package com.musicstreaming.search.dto;

import java.util.Map;

public record SongSearchResult(
        Long songId,
        String trackId,
        String title,
        String artist,
        String album,
        String genre,
        Integer year,
        Double bpm,
        Integer popularity,
        Integer durationMs,
        Double danceability,
        Double energy,
        Boolean explicit,
        String country,
        String label
) {
    public static SongSearchResult fromMap(Map<String, Object> src) {
        return new SongSearchResult(
                toLong(src.get("songId")),
                (String) src.get("trackId"),
                (String) src.get("title"),
                (String) src.get("artist"),
                (String) src.get("album"),
                (String) src.get("genre"),
                toInt(src.get("year")),
                toDouble(src.get("tempo")),
                toInt(src.get("popularity")),
                toInt(src.get("durationMs")),
                toDouble(src.get("danceability")),
                toDouble(src.get("energy")),
                (Boolean) src.get("explicit"),
                (String) src.get("country"),
                (String) src.get("label")
        );
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }
}
