package dev.erkut.authservice.authentication.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthRequestToStringTest {

    @Test
    void registerRequestRedactsPasswordInToString() {
        String rawPassword = "RegisterSecret123";

        String representation = new RegisterRequest(
                "  user@example.com  ",
                rawPassword
        ).toString();

        assertFalse(representation.contains(rawPassword));
        assertTrue(representation.contains("password=[REDACTED]"));
    }

    @Test
    void loginRequestRedactsPasswordInToString() {
        String rawPassword = "LoginSecret123";

        String representation = new LoginRequest(
                "  user@example.com  ",
                rawPassword
        ).toString();

        assertFalse(representation.contains(rawPassword));
        assertTrue(representation.contains("password=[REDACTED]"));
    }
}
