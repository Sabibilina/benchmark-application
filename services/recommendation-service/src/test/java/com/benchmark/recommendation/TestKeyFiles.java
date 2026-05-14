package com.benchmark.recommendation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class TestKeyFiles {

    private TestKeyFiles() {
    }

    public static Path writePublicKey(Path directory) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Path publicKey = directory.resolve("public.pem");
            Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            return publicKey;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create test public key", exception);
        }
    }

    private static String pem(String label, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
