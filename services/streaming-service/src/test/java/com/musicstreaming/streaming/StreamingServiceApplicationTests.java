package com.musicstreaming.streaming;

import com.musicstreaming.streaming.config.JwtTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"playback-events-test"})
class StreamingServiceApplicationTests {

    static {
        JwtTestHelper.mintToken("bootstrap");
    }

    @Test
    void contextLoads() {}
}
