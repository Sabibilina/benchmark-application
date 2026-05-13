package com.benchmark.streaming.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic playbackEventsTopic(StreamingProperties properties) {
        return new NewTopic(properties.playbackEventsTopic(), 1, (short) 1);
    }
}
