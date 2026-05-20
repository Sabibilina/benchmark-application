package com.musicstreaming.e2e.config;

public final class ServiceUrls {
    public static final String AUTH         = env("AUTH_SERVICE_HOST_PORT",         "http://localhost:8081");
    public static final String CATALOG      = env("CATALOG_SERVICE_HOST_PORT",      "http://localhost:8082");
    public static final String STREAM       = env("STREAMING_SERVICE_HOST_PORT",    "http://localhost:8083");
    public static final String PLAYLIST     = env("PLAYLIST_SERVICE_HOST_PORT",     "http://localhost:8084");
    public static final String SEARCH       = env("SEARCH_SERVICE_HOST_PORT",       "http://localhost:8085");
    public static final String ANALYTICS    = env("ANALYTICS_SERVICE_HOST_PORT",    "http://localhost:8086");
    public static final String RECOMMEND    = env("RECOMMENDATION_SERVICE_HOST_PORT","http://localhost:8087");
    public static final String NOTIFICATION = env("NOTIFICATION_SERVICE_HOST_PORT", "http://localhost:8088");

    private ServiceUrls() {}

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
