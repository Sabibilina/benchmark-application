package com.benchmark.notification.config;

import com.benchmark.notification.messaging.PlaylistUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> LOGGER.warn(
                        "Skipping unreadable notification event at topic={}, partition={}, offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        exception),
                new FixedBackOff(0L, 0L));
        errorHandler.addNotRetryableExceptions(DeserializationException.class, IllegalArgumentException.class);
        return errorHandler;
    }
}
