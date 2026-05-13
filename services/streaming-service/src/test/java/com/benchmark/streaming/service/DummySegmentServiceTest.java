package com.benchmark.streaming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.benchmark.streaming.config.StreamingProperties;
import org.junit.jupiter.api.Test;

class DummySegmentServiceTest {

    @Test
    void generateReturnsConfiguredPayloadSize() {
        DummySegmentService service = new DummySegmentService(new StreamingProperties("events", 3, 128));

        byte[] payload = service.generate("song-1", 1);

        assertThat(payload).hasSize(128);
    }

    @Test
    void generateRejectsInvalidSegmentIndex() {
        DummySegmentService service = new DummySegmentService(new StreamingProperties("events", 3, 128));

        assertThatThrownBy(() -> service.generate("song-1", 3))
                .isInstanceOf(StreamingOperationException.class)
                .hasMessageContaining("outside");
    }
}
