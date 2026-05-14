package com.benchmark.recommendation.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemKeyLoader {

    private PemKeyLoader() {
    }

    public static RSAPublicKey loadPublicKey(String path) {
        try {
            String pem = Files.readString(Path.of(path));
            String publicKey = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(publicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load JWT public key from " + path, exception);
        }
    }
}
