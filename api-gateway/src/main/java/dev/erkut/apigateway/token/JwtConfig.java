package dev.erkut.apigateway.token;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        RSAPublicKey publicKey = readPublicKey(properties.publicKey());

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<Object>(
                        "aud",
                        audience -> audience instanceof Collection<?> audiences
                                && audiences.contains(properties.audience())
                );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceValidator
        ));

        return decoder;
    }

    private RSAPublicKey readPublicKey(Resource resource) {
        try {
            byte[] decoded = decodePem(resource, "PUBLIC KEY");

            X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(decoded);

            return (RSAPublicKey) KeyFactory
                    .getInstance("RSA")
                    .generatePublic(keySpec);

        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Failed to load RSA public key",
                    exception
            );
        }
    }

    private byte[] decodePem(Resource resource, String keyType) throws IOException {
        if (resource == null) {
            throw new IllegalArgumentException("RSA " + keyType + " resource is not configured");
        }

        String pem = readPem(resource).trim();
        String beginMarker = "-----BEGIN " + keyType + "-----";
        String endMarker = "-----END " + keyType + "-----";

        if (!pem.startsWith(beginMarker) || !pem.endsWith(endMarker)) {
            throw new IllegalArgumentException(
                    "RSA key must use PEM format with " + beginMarker + " and " + endMarker
            );
        }

        String encodedKey = pem
                .substring(beginMarker.length(), pem.length() - endMarker.length())
                .replaceAll("\\s", "");

        if (encodedKey.isBlank()) {
            throw new IllegalArgumentException("RSA " + keyType + " PEM body is empty");
        }

        return Base64.getDecoder().decode(encodedKey);
    }

    private String readPem(Resource resource) throws IOException {
        try (var inputStream = resource.getInputStream()) {
            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
