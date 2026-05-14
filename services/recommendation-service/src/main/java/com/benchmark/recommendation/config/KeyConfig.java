package com.benchmark.recommendation.config;

import com.benchmark.recommendation.security.PemKeyLoader;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyConfig {

    @Bean
    RSAPublicKey jwtPublicKey(JwtProperties jwtProperties) {
        return PemKeyLoader.loadPublicKey(jwtProperties.publicKeyPath());
    }
}
