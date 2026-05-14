package com.musicstreaming.recommendation.config;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public final class RedisTestContainer {

    public static final GenericContainer<?> INSTANCE =
            new GenericContainer<>("redis:7.2-alpine")
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    static {
        INSTANCE.start();
    }

    private RedisTestContainer() {}
}
