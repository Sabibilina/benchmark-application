package com.benchmark.analytics.config;

import com.benchmark.analytics.security.PemKeyLoader;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyConfig {

    @Bean
    RSAPublicKey jwtPublicKey(JwtProperties properties) {
        return PemKeyLoader.loadPublicKey(properties.publicKeyPath());
    }
}
