package com.musicstreaming.search.config;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public final class OpenSearchTestContainer {

    public static final GenericContainer<?> INSTANCE =
            new GenericContainer<>("opensearchproject/opensearch:2.13.0")
                    .withEnv("discovery.type", "single-node")
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms256m -Xmx256m")
                    .withExposedPorts(9200)
                    .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200));

    static {
        INSTANCE.start();
    }

    private OpenSearchTestContainer() {}
}
