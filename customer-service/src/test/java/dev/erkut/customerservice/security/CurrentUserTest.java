package dev.erkut.customerservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authUserIdReadsUuidFromJwtSubject() {
        Jwt jwt = jwt(AUTH_USER_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertEquals(AUTH_USER_ID, currentUser.authUserId());
    }

    @Test
    void missingAuthenticationThrowsInvalidCurrentUserException() {
        assertThrows(InvalidCurrentUserException.class, currentUser::authUserId);
    }

    @Test
    void malformedSubjectThrowsInvalidCurrentUserException() {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt("not-a-uuid"), List.of()));

        assertThrows(InvalidCurrentUserException.class, currentUser::authUserId);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
