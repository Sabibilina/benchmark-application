package com.musicstreaming.notification.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtPublicKeyConfig {

    @Value("${jwt.public-key-path}")
    private String publicKeyPath;

    private RSAPublicKey publicKey;

    @PostConstruct
    public void init() throws GeneralSecurityException, IOException {
        String pem = Files.readString(Path.of(publicKeyPath));
        byte[] decoded = decodePem(pem);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(decoded));
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }
}
