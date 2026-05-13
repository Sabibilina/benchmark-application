package com.benchmark.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String privateKeyPath,
        String publicKeyPath,
        String issuer,
        Duration accessTokenTtl
) {
}
