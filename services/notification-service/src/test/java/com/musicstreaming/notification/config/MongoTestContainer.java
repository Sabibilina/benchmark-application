package com.musicstreaming.notification.config;

import org.testcontainers.containers.MongoDBContainer;

public final class MongoTestContainer {

    public static final MongoDBContainer INSTANCE = new MongoDBContainer("mongo:7.0");

    static {
        INSTANCE.start();
    }

    private MongoTestContainer() {}
}
