package dev.erkut.orderservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {

    public UUID authUserId() {
        String subject = jwtAuthentication().getToken().getSubject();

        if (subject == null || subject.isBlank()) {
            throw new InvalidCurrentUserException(
                    "JWT subject is missing"
            );
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCurrentUserException(
                    "JWT subject is not a valid UUID"
            );
        }
    }

    public String accessToken() {
        return jwtAuthentication().getToken().getTokenValue();
    }

    private JwtAuthenticationToken jwtAuthentication() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new InvalidCurrentUserException(
                    "Authenticated JWT user is required"
            );
        }

        return jwtAuthentication;
    }
}
