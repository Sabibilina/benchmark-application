package com.musicstreaming.auth.unit;

import com.musicstreaming.auth.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtConfigTest {

    @TempDir
    Path tempDir;

    private JwtConfig buildConfig(Path dir) throws Exception {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "keyDir", dir.toString());
        ReflectionTestUtils.setField(config, "expirationMs", 3600000L);
        config.init();
        return config;
    }

    @Test
    void init_generatesKeyPairAndWritesPemFiles() throws Exception {
        JwtConfig config = buildConfig(tempDir);

        assertNotNull(config.getPrivateKey());
        assertNotNull(config.getPublicKey());
        assertTrue(Files.exists(tempDir.resolve("private.pem")));
        assertTrue(Files.exists(tempDir.resolve("public.pem")));
    }

    @Test
    void init_loadsExistingKeyPairAndProducesSamePublicKey() throws Exception {
        JwtConfig first = buildConfig(tempDir);
        byte[] originalEncoded = first.getPublicKey().getEncoded();

        JwtConfig second = buildConfig(tempDir);

        assertNotNull(second.getPublicKey());
        assertEquals(
                java.util.Base64.getEncoder().encodeToString(originalEncoded),
                java.util.Base64.getEncoder().encodeToString(second.getPublicKey().getEncoded())
        );
    }

    @Test
    void token_roundtrip_claimsAreCorrect() throws Exception {
        JwtConfig config = buildConfig(tempDir);

        String token = Jwts.builder()
                .subject("user-id-123")
                .claim("username", "alice")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(config.getPrivateKey())
                .compact();

        Claims claims = Jwts.parser()
                .verifyWith(config.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("user-id-123", claims.getSubject());
        assertEquals("alice", claims.get("username", String.class));
    }

    @Test
    void expired_token_isRejected() throws Exception {
        JwtConfig config = buildConfig(tempDir);

        String token = Jwts.builder()
                .subject("user-id-123")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(config.getPrivateKey())
                .compact();

        assertThrows(JwtException.class, () ->
                Jwts.parser()
                        .verifyWith(config.getPublicKey())
                        .build()
                        .parseSignedClaims(token)
        );
    }
}
