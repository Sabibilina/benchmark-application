package com.benchmark.analytics.support;

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

    public static KeyPaths create() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("analytics-test-keys");
            Path publicKey = directory.resolve("public.pem");
            Files.writeString(publicKey, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            return new KeyPaths(publicKey);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to create test key files", ex);
        }
    }

    private static String toPem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    public record KeyPaths(Path publicKey) {
    }
}
