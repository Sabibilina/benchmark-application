package com.benchmark.auth.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class TestKeyFiles {

    private TestKeyFiles() {
    }

    public static KeyPaths create() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("auth-test-keys");
            Path privateKey = directory.resolve("private.pem");
            Path publicKey = directory.resolve("public.pem");
            Files.writeString(privateKey, toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(publicKey, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            return new KeyPaths(privateKey, publicKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create test key files", ex);
        }
    }

    private static String toPem(String label, byte[] bytes) throws IOException {
        String body = Base64.getMimeEncoder(64, System.lineSeparator().getBytes()).encodeToString(bytes);
        return "-----BEGIN " + label + "-----" + System.lineSeparator()
                + body + System.lineSeparator()
                + "-----END " + label + "-----" + System.lineSeparator();
    }

    public record KeyPaths(Path privateKey, Path publicKey) {
    }
}
