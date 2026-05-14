package com.musicstreaming.analytics.config;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public final class ClickHouseTestContainer {

    public static final GenericContainer<?> INSTANCE =
            new GenericContainer<>("clickhouse/clickhouse-server:24.3")
                    .withEnv("CLICKHOUSE_USER", "default")
                    .withEnv("CLICKHOUSE_PASSWORD", "")
                    .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
                    .withExposedPorts(8123)
                    .waitingFor(
                            Wait.forHttp("/ping")
                                .forPort(8123)
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.ofSeconds(180)));

    static {
        INSTANCE.start();
    }

    private ClickHouseTestContainer() {}
}
