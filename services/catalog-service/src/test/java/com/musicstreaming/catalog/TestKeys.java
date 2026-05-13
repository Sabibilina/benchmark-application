package com.musicstreaming.catalog;

import io.jsonwebtoken.Jwts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Generates a test RSA key pair once per JVM. The public key is written to a fixed temp path.
 * Test classes set jwt.public-key-path to PUBLIC_KEY_PATH via @DynamicPropertySource so the
 * Spring context picks it up before JwtPublicKeyConfig.init() runs.
 */
public final class TestKeys {

    public static final String PUBLIC_KEY_PATH;
    private static final RSAPrivateKey PRIVATE_KEY;

    static {
        try {
            Path keyDir = Path.of(System.getProperty("java.io.tmpdir"), "catalog-service-test-keys");
            Files.createDirectories(keyDir);
            Path privatePath = keyDir.resolve("private.pem");
            Path publicPath  = keyDir.resolve("public.pem");

            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PRIVATE_KEY = (RSAPrivateKey) kf.generatePrivate(
                        new PKCS8EncodedKeySpec(decodePem(Files.readString(privatePath))));
            } else {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair pair = gen.generateKeyPair();
                PRIVATE_KEY = (RSAPrivateKey) pair.getPrivate();
                RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
                writePem("PRIVATE KEY", PRIVATE_KEY.getEncoded(), privatePath);
                writePem("PUBLIC KEY",  publicKey.getEncoded(),  publicPath);
            }
            PUBLIC_KEY_PATH = publicPath.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test RSA key pair", e);
        }
    }

    private TestKeys() {}

    public static String generateToken(String userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + 3_600_000L);
        return Jwts.builder()
                .subject(userId)
                .claim("username", "testuser")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(PRIVATE_KEY, Jwts.SIG.RS256)
                .compact();
    }

    public static String generateExpiredToken(String userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date(now - 7_200_000L))
                .expiration(new Date(now - 3_600_000L))
                .signWith(PRIVATE_KEY, Jwts.SIG.RS256)
                .compact();
    }

    private static void writePem(String type, byte[] keyBytes, Path path) throws Exception {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyBytes);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----\n");
    }

    private static byte[] decodePem(String pem) {
        return Base64.getDecoder().decode(
                pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", ""));
    }
}
