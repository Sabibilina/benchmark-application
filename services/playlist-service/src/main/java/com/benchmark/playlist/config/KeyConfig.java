package com.benchmark.playlist.config;

import com.benchmark.playlist.security.PemKeyLoader;
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
