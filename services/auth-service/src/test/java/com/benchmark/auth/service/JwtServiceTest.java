package com.benchmark.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benchmark.auth.config.JwtProperties;
import com.benchmark.auth.config.SecurityConfig;
import com.benchmark.auth.entity.UserAccount;
import com.benchmark.auth.security.PemKeyLoader;
import com.benchmark.auth.support.TestKeyFiles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    @Test
    void issueAccessTokenCreatesRs256TokenVerifiedByPublicKey() {
        TestKeyFiles.KeyPaths keys = TestKeyFiles.create();
        var privateKey = PemKeyLoader.loadPrivateKey(keys.privateKey().toString());
        var publicKey = PemKeyLoader.loadPublicKey(keys.publicKey().toString());
        SecurityConfig securityConfig = new SecurityConfig();
        JwtProperties properties = new JwtProperties(
                keys.privateKey().toString(),
                keys.publicKey().toString(),
                "benchmark-auth",
                Duration.ofHours(1)
        );
        Clock clock = Clock.fixed(Instant.parse("2030-05-10T12:00:00Z"), ZoneOffset.UTC);
        JwtService jwtService = new JwtService(securityConfig.jwtEncoder(publicKey, privateKey), properties, clock);
        JwtDecoder decoder = securityConfig.jwtDecoder(publicKey);
        UserAccount user = new UserAccount("user@example.com", "hash");
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        String token = jwtService.issueAccessToken(user);
        var decoded = decoder.decode(token);

        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("benchmark-auth");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("user@example.com");
    }

    @Test
    void publicKeyVerificationRejectsTamperedToken() {
        TestKeyFiles.KeyPaths keys = TestKeyFiles.create();
        var privateKey = PemKeyLoader.loadPrivateKey(keys.privateKey().toString());
        var publicKey = PemKeyLoader.loadPublicKey(keys.publicKey().toString());
        SecurityConfig securityConfig = new SecurityConfig();
        JwtProperties properties = new JwtProperties(
                keys.privateKey().toString(),
                keys.publicKey().toString(),
                "benchmark-auth",
                Duration.ofHours(1)
        );
        JwtService jwtService = new JwtService(
                securityConfig.jwtEncoder(publicKey, privateKey),
                properties,
                Clock.systemUTC()
        );
        UserAccount user = new UserAccount("user@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        String token = jwtService.issueAccessToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> securityConfig.jwtDecoder(publicKey).decode(tampered))
                .isInstanceOf(JwtException.class);
    }
}
