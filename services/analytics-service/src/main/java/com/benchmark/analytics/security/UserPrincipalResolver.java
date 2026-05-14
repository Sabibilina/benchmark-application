package com.benchmark.analytics.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UserPrincipalResolver {

    public AuthenticatedUser resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalArgumentException("JWT authentication is required");
        }
        return new AuthenticatedUser(jwt.getSubject());
    }
}
