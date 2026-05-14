package com.musicstreaming.notification.controller;

import com.musicstreaming.notification.dto.NotificationResponse;
import com.musicstreaming.notification.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        return service.getNotificationsForUser(authentication.getName())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
