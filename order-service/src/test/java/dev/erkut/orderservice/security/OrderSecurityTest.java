package dev.erkut.orderservice.security;

import dev.erkut.orderservice.order.application.OrderService;
import dev.erkut.orderservice.order.api.OrderController;
import dev.erkut.orderservice.order.api.admin.AdminOrderController;
import dev.erkut.orderservice.order.api.internal.InternalOrderController;
import dev.erkut.orderservice.cart.api.admin.AdminCartController;
import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.integration.customer.CurrentCustomerResolver;
import dev.erkut.orderservice.cart.api.error.CartExceptionHandler;
import dev.erkut.orderservice.order.api.error.OrderExceptionHandler;
import dev.erkut.orderservice.security.token.JwtConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, AdminOrderController.class, AdminCartController.class, InternalOrderController.class})
@Import({SecurityConfig.class, JwtConfig.class, OrderExceptionHandler.class, CartExceptionHandler.class})
@EnableWebSecurity
class OrderSecurityTest {

    private static final String ISSUER = "ecommerce-auth";
    private static final String AUDIENCE = "ecommerce-api";
    private static final String SUBJECT = "00000000-0000-0000-0000-000000000001";
    private static final String CUSTOMER_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private static final TestKeyFiles TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder JWT_ENCODER = encoder(TEST_KEYS.keyPair());
    private static final TestKeyFiles WRONG_TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder WRONG_KEY_ENCODER = encoder(WRONG_TEST_KEYS.keyPair());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CurrentCustomerResolver currentCustomerResolver;

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
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtIsAccepted() throws Exception {
        when(currentCustomerResolver.customerId()).thenReturn(java.util.UUID.fromString(CUSTOMER_ID));
        when(orderService.getOrders(java.util.UUID.fromString(CUSTOMER_ID), 0, 10)).thenReturn(Page.empty());

        mockMvc.perform(get("/orders")
                        .header("Authorization", bearerToken(validToken(JWT_ENCODER,
                                ISSUER, List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotAccessAdminOrders() throws Exception {
        mockMvc.perform(get("/admin/orders")
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService, currentCustomerResolver);
    }

    @Test
    void adminCanListAllOrdersWithoutCustomerResolution() throws Exception {
        when(orderService.getAllOrders(0, 10)).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/orders")
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isOk());

        verify(orderService).getAllOrders(0, 10);
        verifyNoInteractions(currentCustomerResolver);
    }

    @Test
    void userCannotReadAdminOrder() throws Exception {
        mockMvc.perform(get("/admin/orders/{orderId}", java.util.UUID.randomUUID())
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyOrderWithoutCustomerResolution() throws Exception {
        java.util.UUID orderId = java.util.UUID.randomUUID();
        when(orderService.getOrderByIdForAdmin(orderId)).thenReturn(null);

        mockMvc.perform(get("/admin/orders/{orderId}", orderId)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isOk());

        verify(orderService).getOrderByIdForAdmin(orderId);
        verifyNoInteractions(currentCustomerResolver);
    }

    @Test
    void adminUnknownOrderReturnsNotFound() throws Exception {
        java.util.UUID orderId = java.util.UUID.randomUUID();
        when(orderService.getOrderByIdForAdmin(orderId))
                .thenThrow(new dev.erkut.orderservice.order.domain.exception.OrderNotFoundException("Order not found"));

        mockMvc.perform(get("/admin/orders/{orderId}", orderId)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminUsingOwnerOrderEndpointRemainsOwnershipScoped() throws Exception {
        java.util.UUID orderId = java.util.UUID.randomUUID();
        java.util.UUID customerId = java.util.UUID.randomUUID();
        when(currentCustomerResolver.customerId()).thenReturn(customerId);
        when(orderService.getOrderById(orderId, customerId))
                .thenThrow(new dev.erkut.orderservice.order.domain.exception.OrderNotFoundException("Order not found"));

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isNotFound());

        verify(currentCustomerResolver).customerId();
        verify(orderService).getOrderById(orderId, customerId);
    }

    @Test
    void userCannotReadAdminCart() throws Exception {
        mockMvc.perform(get("/admin/carts/{cartId}", java.util.UUID.randomUUID())
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyCartWithoutCustomerResolution() throws Exception {
        java.util.UUID cartId = java.util.UUID.randomUUID();
        when(cartService.getCartByIdForAdmin(cartId))
                .thenReturn(Cart.create(java.util.UUID.randomUUID(), Instant.now()));

        mockMvc.perform(get("/admin/carts/{cartId}", cartId)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isOk());

        verify(cartService).getCartByIdForAdmin(cartId);
        verifyNoInteractions(currentCustomerResolver);
    }

    @Test
    void adminUnknownCartReturnsNotFound() throws Exception {
        java.util.UUID cartId = java.util.UUID.randomUUID();
        when(cartService.getCartByIdForAdmin(cartId))
                .thenThrow(new dev.erkut.orderservice.cart.application.exception.CartNotFoundException("Cart not found"));

        mockMvc.perform(get("/admin/carts/{cartId}", cartId)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalOrderOwnershipUsesResolvedCustomer() throws Exception {
        java.util.UUID orderId = java.util.UUID.randomUUID();
        java.util.UUID customerId = java.util.UUID.randomUUID();
        when(currentCustomerResolver.customerId()).thenReturn(customerId);
        when(orderService.belongsToCustomer(orderId, customerId)).thenReturn(true);

        mockMvc.perform(get("/internal/orders/{orderId}/ownership", orderId)
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isOk());

        verify(currentCustomerResolver).customerId();
        verify(orderService).belongsToCustomer(orderId, customerId);
    }

    @Test
    void internalOrderOwnershipReturnsNotFoundForForeignOrder() throws Exception {
        java.util.UUID orderId = java.util.UUID.randomUUID();
        java.util.UUID customerId = java.util.UUID.randomUUID();
        when(currentCustomerResolver.customerId()).thenReturn(customerId);
        when(orderService.belongsToCustomer(orderId, customerId)).thenReturn(false);

        mockMvc.perform(get("/internal/orders/{orderId}/ownership", orderId)
                        .header("Authorization", bearerToken(userToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalOrderOwnershipRequiresJwt() throws Exception {
        mockMvc.perform(get("/internal/orders/{orderId}/ownership", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService, currentCustomerResolver);
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders")
                        .header("Authorization", bearerToken(validToken(WRONG_KEY_ENCODER,
                                ISSUER, List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();

        mockMvc.perform(get("/orders")
                        .header("Authorization", bearerToken(validToken(JWT_ENCODER,
                                ISSUER, List.of(AUDIENCE),
                                now.minus(10, ChronoUnit.MINUTES),
                                now.minus(5, ChronoUnit.MINUTES)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders")
                        .header("Authorization", bearerToken(validToken(JWT_ENCODER,
                                "wrong-issuer", List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders")
                        .header("Authorization", bearerToken(validToken(JWT_ENCODER,
                                ISSUER, List.of("another-api"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesClaimMapsToRoleAuthority() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(SUBJECT)
                .claim("roles", List.of("USER"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .build();

        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER");
    }

    @Test
    void adminRoleClaimMapsToAdminAuthority() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(SUBJECT)
                .claim("roles", List.of("ADMIN"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .build();

        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    private static JwtEncoder encoder(KeyPair keyPair) {
        return NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .jwkPostProcessor(jwk -> jwk.keyID("order-security-test-key"))
                .build();
    }

    private static String validToken(
            JwtEncoder encoder,
            String issuer,
            List<String> audience,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return token(encoder, issuer, audience, List.of("USER"), issuedAt, expiresAt);
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
                .subject(SUBJECT)
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("order-security-test-jti")
                .claim("roles", roles)
                .build();

        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("order-security-test-key")
                .build();

        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private static String bearerToken(String token) {
        return "Bearer " + token;
    }

    private String userToken() {
        return token(
                JWT_ENCODER,
                ISSUER,
                List.of(AUDIENCE),
                List.of("USER"),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(300)
        );
    }

    private String adminToken() {
        return token(
                JWT_ENCODER,
                ISSUER,
                List.of(AUDIENCE),
                List.of("ADMIN"),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(300)
        );
    }

    private record TestKeyFiles(Path directory, Path publicKey, KeyPair keyPair) {
        static TestKeyFiles create() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                Path directory = Files.createTempDirectory("order-security-test-keys-");
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
