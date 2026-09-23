package dev.erkut.authservice.authentication.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) { }
