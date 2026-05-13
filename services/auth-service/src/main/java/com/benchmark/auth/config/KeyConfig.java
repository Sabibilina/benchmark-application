package com.benchmark.auth.config;

import com.benchmark.auth.security.PemKeyLoader;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyConfig {

    @Bean
    RSAPrivateKey jwtPrivateKey(JwtProperties properties) {
        return PemKeyLoader.loadPrivateKey(properties.privateKeyPath());
    }

    @Bean
    RSAPublicKey jwtPublicKey(JwtProperties properties) {
        return PemKeyLoader.loadPublicKey(properties.publicKeyPath());
    }
}
