package com.benchmark.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(@NotBlank String playlistEventsTopic) {
}
