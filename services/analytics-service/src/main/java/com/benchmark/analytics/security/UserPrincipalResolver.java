package com.benchmark.analytics.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UserPrincipalResolver {

    public AuthenticatedUser resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalArgumentException("JWT authentication is required");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is required");
        }
        try {
            UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT subject must be the canonical user id", ex);
        }
        return new AuthenticatedUser(subject);
    }
}
