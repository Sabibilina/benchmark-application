package com.benchmark.catalog.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemKeyLoader {

    private PemKeyLoader() {
    }

    public static RSAPublicKey loadPublicKey(String path) {
        try {
            byte[] keyBytes = decodePem(path);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to load RSA public key from " + path, ex);
        }
    }

    private static byte[] decodePem(String path) throws IOException {
        String pem = Files.readString(Path.of(path));
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
