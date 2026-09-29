package dev.erkut.paymentservice.security;

import dev.erkut.paymentservice.integration.order.OrderOwnershipClient;
import dev.erkut.paymentservice.payment.api.PaymentController;
import dev.erkut.paymentservice.payment.api.error.PaymentExceptionHandler;
import dev.erkut.paymentservice.payment.api.response.PaymentResponse;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException;
import dev.erkut.paymentservice.payment.domain.PaymentStatus;
import dev.erkut.paymentservice.provider.payment.stripe.StripeWebhookController;
import dev.erkut.paymentservice.provider.payment.stripe.StripeWebhookService;
import dev.erkut.paymentservice.security.token.JwtConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PaymentController.class, StripeWebhookController.class})
@Import({SecurityConfig.class, JwtConfig.class, PaymentExceptionHandler.class})
@EnableWebSecurity
class PaymentSecurityTest {

    private static final String ISSUER = "ecommerce-auth";
    private static final String AUDIENCE = "ecommerce-api";
    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static final TestKeyFiles TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder JWT_ENCODER = encoder(TEST_KEYS.keyPair());
    private static final TestKeyFiles WRONG_TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder WRONG_KEY_ENCODER = encoder(WRONG_TEST_KEYS.keyPair());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private OrderOwnershipClient orderOwnershipClient;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoBean
    private StripeWebhookService webhookService;

    @AfterAll
    static void deleteTestKeys() throws IOException {
        TEST_KEYS.delete();
        WRONG_TEST_KEYS.delete();
    }

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key", () -> TEST_KEYS.publicKey().toUri().toString());
        registry.add("security.jwt.issuer", () -> ISSUER);
        registry.add("security.jwt.audience", () -> AUDIENCE);
    }

    @Test
    void missingJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", bearerToken(token(
                                WRONG_KEY_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                List.of("USER"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();

        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", bearerToken(token(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                List.of("USER"),
                                now.minus(10, ChronoUnit.MINUTES),
                                now.minus(5, ChronoUnit.MINUTES)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", bearerToken(token(
                                JWT_ENCODER,
                                "wrong-issuer",
                                List.of(AUDIENCE),
                                List.of("USER"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", bearerToken(token(
                                JWT_ENCODER,
                                ISSUER,
                                List.of("another-api"),
                                List.of("USER"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtIsAcceptedAndUsesAuthenticatedBearerToken() throws Exception {
        when(currentUser.accessToken()).thenReturn("access-token");
        when(paymentService.getPaymentByOrderId(ORDER_ID, "access-token"))
                .thenReturn(new PaymentResponse(ORDER_ID, PaymentStatus.AWAITING_CUSTOMER_ACTION, "https://checkout.test"));

        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isOk());

        verify(paymentService).getPaymentByOrderId(ORDER_ID, "access-token");
    }

    @Test
    void foreignPaymentIsReturnedAsNotFound() throws Exception {
        when(currentUser.accessToken()).thenReturn("access-token");
        when(paymentService.getPaymentByOrderId(ORDER_ID, "access-token"))
                .thenThrow(new PaymentNotFoundException("Payment not found"));

        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID)
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void webhookDoesNotRequireJwt() throws Exception {
        mockMvc.perform(post("/payments/webhooks/stripe")
                        .header("Stripe-Signature", "test-signature")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(webhookService).handle("{}", "test-signature");
        verifyNoInteractions(paymentService, orderOwnershipClient, currentUser);
    }

    @Test
    void rolesClaimMapsToRoleAuthorities() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(AUTH_USER_ID.toString())
                .claim("roles", List.of("USER", "ADMIN"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .build();

        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER", "ROLE_ADMIN");
    }

    private static String userToken() {
        return token(
                JWT_ENCODER,
                ISSUER,
                List.of(AUDIENCE),
                List.of("USER"),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(300)
        );
    }

    private static String token(
            JwtEncoder encoder,
            String issuer,
            List<String> audience,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(AUTH_USER_ID.toString())
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("payment-security-test-jti")
                .claim("roles", roles)
                .build();

        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("payment-security-test-key")
                .build();

        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private static JwtEncoder encoder(KeyPair keyPair) {
        return NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .jwkPostProcessor(jwk -> jwk.keyID("payment-security-test-key"))
                .build();
    }

    private static String bearerToken(String token) {
        return "Bearer " + token;
    }

    private record TestKeyFiles(Path directory, Path publicKey, KeyPair keyPair) {
        static TestKeyFiles create() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                Path directory = Files.createTempDirectory("payment-security-test-keys-");
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
}
