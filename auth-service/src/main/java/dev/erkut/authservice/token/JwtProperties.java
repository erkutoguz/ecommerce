package dev.erkut.authservice.token;

import org.springframework.core.io.Resource;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        String keyId,
        Resource publicKey,
        Resource privateKey
) {
}