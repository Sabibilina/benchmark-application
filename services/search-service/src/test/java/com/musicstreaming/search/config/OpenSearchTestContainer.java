package com.musicstreaming.search.config;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public final class OpenSearchTestContainer {

    @SuppressWarnings("resource") // Ryuk reaper handles cleanup
    public static final GenericContainer<?> INSTANCE =
            new GenericContainer<>("opensearchproject/opensearch:2.13.0")
                    .withEnv("discovery.type", "single-node")
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withExposedPorts(9200)
                    .waitingFor(
                        Wait.forHttp("/_cluster/health?wait_for_status=yellow&timeout=30s")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        INSTANCE.start();
    }

    private OpenSearchTestContainer() {}
}
