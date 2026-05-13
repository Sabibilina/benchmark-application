package com.musicstreaming.playlist;

import com.musicstreaming.playlist.config.JwtTestHelper;
import com.musicstreaming.playlist.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"playlist-events-test"})
class PlaylistServiceApplicationTests {

    static {
        JwtTestHelper.mintToken("bootstrap");
    }

    @Test
    void contextLoads() {}
}
