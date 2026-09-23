package dev.erkut.apigateway;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiGatewayApplicationTests.TestRouteController.class)
class ApiGatewayApplicationTests {

    private static final TestKeyFiles TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder JWT_ENCODER = NimbusJwtEncoder
            .withKeyPair(
                    (java.security.interfaces.RSAPublicKey) TEST_KEYS.keyPair().getPublic(),
                    (java.security.interfaces.RSAPrivateKey) TEST_KEYS.keyPair().getPrivate()
            )
            .jwkPostProcessor(jwk -> jwk.keyID("gateway-test-key"))
            .build();

    @Autowired
    private MockMvc mockMvc;

    @AfterAll
    static void deleteTestKey() throws IOException {
        TEST_KEYS.delete();
    }

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY_PATH", () -> TEST_KEYS.publicKey().toString());
    }

    @Test
    void publicAuthRegisterDoesNotRequireJwt() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"opaque-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void publicAuthLoginDoesNotRequireJwt() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"opaque-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void publicProductGetDoesNotRequireJwt() throws Exception {
        mockMvc.perform(get("/products/test"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validUserTokenPassesAuthenticatedRoute() throws Exception {
        mockMvc.perform(get("/orders/test")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userToken())))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenCannotMutateProducts() throws Exception {
        mockMvc.perform(post("/products/test")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenCanMutateProducts() throws Exception {
        mockMvc.perform(post("/products/test")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders/test")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token(
                                "different-issuer",
                                List.of("ecommerce-api"),
                                List.of("USER"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders/test")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token(
                                "ecommerce-auth",
                                List.of("different-api"),
                                List.of("USER"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();

        mockMvc.perform(get("/orders/test")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(token(
                                "ecommerce-auth",
                                List.of("ecommerce-api"),
                                List.of("USER"),
                                now.minus(10, ChronoUnit.MINUTES),
                                now.minus(5, ChronoUnit.MINUTES)
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void defaultDenyAllRejectsUnmatchedRoute() throws Exception {
        mockMvc.perform(get("/not-configured")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorDispatcherIsNotBlockedByDenyAll() throws Exception {
        mockMvc.perform(get("/orders/test")
                        .with(request -> asErrorDispatcher(request)))
                .andExpect(status().isOk());
    }

    @Test
    void stripeWebhookDoesNotRequireJwt() throws Exception {
        mockMvc.perform(post("/payments/webhooks/stripe"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequest asErrorDispatcher(MockHttpServletRequest request) {
        request.setDispatcherType(DispatcherType.ERROR);
        return request;
    }

    private String userToken() {
        return token(
                "ecommerce-auth",
                List.of("ecommerce-api"),
                List.of("USER"),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(300)
        );
    }

    private String adminToken() {
        return token(
                "ecommerce-auth",
                List.of("ecommerce-api"),
                List.of("ADMIN"),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(300)
        );
    }

    private static String bearerToken(String token) {
        return "Bearer " + token;
    }

    private static String token(
            String issuer,
            List<String> audience,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("00000000-0000-0000-0000-000000000001")
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("gateway-test-jti")
                .claim("roles", roles)
                .build();

        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("gateway-test-key")
                .build();

        return JWT_ENCODER
                .encode(JwtEncoderParameters.from(headers, claims))
                .getTokenValue();
    }

    private record TestKeyFiles(Path directory, Path publicKey, KeyPair keyPair) {
        static TestKeyFiles create() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                Path directory = Files.createTempDirectory("api-gateway-test-keys-");
                Path publicKey = directory.resolve("public.pem");
                Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
                return new TestKeyFiles(directory, publicKey, keyPair);
            } catch (Exception exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        void delete() throws IOException {
            Files.deleteIfExists(publicKey);
            Files.deleteIfExists(directory);
        }

        private static String pem(String type, byte[] encoded) {
            return "-----BEGIN " + type + "-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                    + "\n-----END " + type + "-----\n";
        }
    }

    @RestController
    static class TestRouteController {
        @RequestMapping({"/auth/**", "/products/**", "/orders/**", "/payments/**"})
        String respondOk() {
            return "ok";
        }
    }
}
