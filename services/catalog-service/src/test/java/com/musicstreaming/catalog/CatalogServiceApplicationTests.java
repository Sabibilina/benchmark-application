package com.musicstreaming.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CatalogServiceApplicationTests {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key-path", () -> TestKeys.PUBLIC_KEY_PATH);
    }

    @Test
    void contextLoads() {
    }
}
