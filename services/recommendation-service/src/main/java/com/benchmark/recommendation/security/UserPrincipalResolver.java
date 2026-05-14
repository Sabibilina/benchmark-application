package com.benchmark.recommendation.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UserPrincipalResolver {

    public AuthenticatedUser resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalArgumentException("Authenticated JWT principal is required");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is required");
        }
        try {
            return new AuthenticatedUser(UUID.fromString(subject));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT subject must be a UUID");
        }
    }
}
