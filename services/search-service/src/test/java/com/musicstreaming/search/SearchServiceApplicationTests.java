package com.musicstreaming.search;

import com.musicstreaming.search.config.JwtTestHelper;
import com.musicstreaming.search.config.OpenSearchTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class SearchServiceApplicationTests {

    static {
        JwtTestHelper.mintToken("bootstrap");
        OpenSearchTestContainer.INSTANCE.isRunning(); // ensure container is started
    }

    @DynamicPropertySource
    static void configureOpenSearch(DynamicPropertyRegistry registry) {
        registry.add("opensearch.host", OpenSearchTestContainer.INSTANCE::getHost);
        registry.add("opensearch.port", () -> OpenSearchTestContainer.INSTANCE.getMappedPort(9200));
    }

    @Test
    void contextLoads() {}
}
