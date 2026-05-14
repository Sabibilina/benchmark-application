package com.musicstreaming.search.config;

import io.jsonwebtoken.Jwts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;

public class JwtTestHelper {

    private static final Path KEY_DIR = Path.of("/tmp/search-service-test-keys");
    private static final Path PUBLIC_KEY_PATH = KEY_DIR.resolve("public.pem");

    private static RSAPrivateKey privateKey;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();

            Files.createDirectories(KEY_DIR);
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            Files.writeString(PUBLIC_KEY_PATH, pem);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise test JWT keys", e);
        }
    }

    public static String mintToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private JwtTestHelper() {}
}
