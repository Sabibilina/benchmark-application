package com.musicstreaming.auth.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${jwt.key-dir}")
    private String keyDir;

    @Value("${jwt.expiration-ms:3600000}")
    private long expirationMs;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @PostConstruct
    public void init() throws GeneralSecurityException, IOException {
        Path dir = Path.of(keyDir);
        Files.createDirectories(dir);

        Path privatePath = dir.resolve("private.pem");
        Path publicPath  = dir.resolve("public.pem");

        if (Files.exists(privatePath) && Files.exists(publicPath)) {
            loadKeys(privatePath, publicPath);
            log.info("JWT RSA key pair loaded from {}", dir);
        } else {
            generateAndSaveKeys(privatePath, publicPath);
            log.info("JWT RSA key pair generated and written to {}", dir);
        }
    }

    private void generateAndSaveKeys(Path privatePath, Path publicPath)
            throws GeneralSecurityException, IOException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        KeyPair pair = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicKey  = (RSAPublicKey)  pair.getPublic();
        writePem("PRIVATE KEY", privateKey.getEncoded(), privatePath);
        writePem("PUBLIC KEY",  publicKey.getEncoded(),  publicPath);
    }

    private void loadKeys(Path privatePath, Path publicPath)
            throws GeneralSecurityException, IOException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        byte[] privBytes = decodePem(Files.readString(privatePath));
        byte[] pubBytes  = decodePem(Files.readString(publicPath));
        privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        publicKey  = (RSAPublicKey)  kf.generatePublic(new X509EncodedKeySpec(pubBytes));
    }

    private static void writePem(String type, byte[] keyBytes, Path path) throws IOException {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyBytes);
        String pem = "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem);
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    public RSAPrivateKey getPrivateKey()  { return privateKey;   }
    public RSAPublicKey  getPublicKey()   { return publicKey;    }
    public long          getExpirationMs(){ return expirationMs; }
}
