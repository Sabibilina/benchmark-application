package com.benchmark.streaming.messaging;

public enum PlaybackEventType {
    STARTED("play.started"),
    ENDED("play.ended"),
    SKIPPED("play.skipped");

    private final String value;

    PlaybackEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
