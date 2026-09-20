package dev.erkut.authservice.token;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = readPublicKey(properties.publicKey());
        RSAPrivateKey privateKey = readPrivateKey(properties.privateKey());

        return NimbusJwtEncoder
                .withKeyPair(publicKey, privateKey)
                .jwkPostProcessor(jwk -> jwk.keyID(properties.keyId()))
                .build();
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

    private RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            byte[] decoded = decodePem(resource, "PRIVATE KEY");

            PKCS8EncodedKeySpec keySpec =
                    new PKCS8EncodedKeySpec(decoded);

            return (RSAPrivateKey) KeyFactory
                    .getInstance("RSA")
                    .generatePrivate(keySpec);

        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Failed to load RSA private key",
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
