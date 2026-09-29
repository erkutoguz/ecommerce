package dev.erkut.customerservice.security;

import dev.erkut.customerservice.customer.api.CustomerController;
import dev.erkut.customerservice.customer.api.admin.AdminCustomerController;
import dev.erkut.customerservice.customer.api.error.GlobalExceptionHandler;
import dev.erkut.customerservice.customer.api.response.CustomerResponse;
import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.customer.domain.CustomerStatus;
import dev.erkut.customerservice.customer.domain.exception.CustomerNotFoundException;
import dev.erkut.customerservice.security.token.JwtConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

@WebMvcTest({CustomerController.class, AdminCustomerController.class})
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
@EnableWebSecurity
class CustomerSecurityTest {

    private static final String ISSUER = "ecommerce-auth";
    private static final String AUDIENCE = "ecommerce-api";
    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static final TestKeyFiles TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder JWT_ENCODER = encoder(TEST_KEYS.keyPair());
    private static final TestKeyFiles WRONG_TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder WRONG_KEY_ENCODER = encoder(WRONG_TEST_KEYS.keyPair());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private CurrentUser currentUser;

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
        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtIsAccepted() throws Exception {
        when(currentUser.authUserId()).thenReturn(AUTH_USER_ID);
        when(customerService.getCustomerById(CUSTOMER_ID, AUTH_USER_ID))
                .thenReturn(customerResponse());

        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotAccessAdminCustomerList() throws Exception {
        mockMvc.perform(get("/admin/customers")
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customerService);
    }

    @Test
    void adminCanListAllCustomersWithoutCustomerResolution() throws Exception {
        when(customerService.getCustomersForAdmin(0, 10))
                .thenReturn(new PageImpl<>(List.of(customerResponse(CUSTOMER_ID), customerResponse(OTHER_CUSTOMER_ID))));

        mockMvc.perform(get("/admin/customers")
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isOk());

        verify(customerService).getCustomersForAdmin(0, 10);
        verifyNoInteractions(currentUser);
    }

    @Test
    void userCannotReadAdminCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/admin/customers/{customerId}", OTHER_CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyCustomer() throws Exception {
        when(customerService.getCustomerByIdForAdmin(OTHER_CUSTOMER_ID))
                .thenReturn(customerResponse(OTHER_CUSTOMER_ID));

        mockMvc.perform(get("/admin/customers/{customerId}", OTHER_CUSTOMER_ID)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isOk());

        verify(customerService).getCustomerByIdForAdmin(OTHER_CUSTOMER_ID);
        verifyNoInteractions(currentUser);
    }

    @Test
    void adminUnknownCustomerReturnsNotFound() throws Exception {
        when(customerService.getCustomerByIdForAdmin(OTHER_CUSTOMER_ID))
                .thenThrow(new CustomerNotFoundException("Customer not found"));

        mockMvc.perform(get("/admin/customers/{customerId}", OTHER_CUSTOMER_ID)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotDeactivateThroughAdminEndpoint() throws Exception {
        mockMvc.perform(post("/admin/customers/{customerId}/deactivate", OTHER_CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeactivateAnyCustomer() throws Exception {
        when(customerService.deactivateCustomerForAdmin(OTHER_CUSTOMER_ID))
                .thenReturn(customerResponse(OTHER_CUSTOMER_ID));

        mockMvc.perform(post("/admin/customers/{customerId}/deactivate", OTHER_CUSTOMER_ID)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isOk());

        verify(customerService).deactivateCustomerForAdmin(OTHER_CUSTOMER_ID);
        verifyNoInteractions(currentUser);
    }

    @Test
    void adminUsingOwnerEndpointRemainsOwnershipScoped() throws Exception {
        when(currentUser.authUserId()).thenReturn(AUTH_USER_ID);
        when(customerService.getCustomerById(OTHER_CUSTOMER_ID, AUTH_USER_ID))
                .thenThrow(new CustomerNotFoundException("Customer not found"));

        mockMvc.perform(get("/customers/{customerId}", OTHER_CUSTOMER_ID)
                        .header("Authorization", bearerToken(adminToken())))
                .andExpect(status().isNotFound());

        verify(customerService).getCustomerById(OTHER_CUSTOMER_ID, AUTH_USER_ID);
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                WRONG_KEY_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();

        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                now.minus(10, ChronoUnit.MINUTES),
                                now.minus(5, ChronoUnit.MINUTES)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                "wrong-issuer",
                                List.of(AUDIENCE),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID)
                        .header("Authorization", bearerToken(validToken(
                                JWT_ENCODER,
                                ISSUER,
                                List.of("another-api"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesClaimMapsToRoleAuthority() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(AUTH_USER_ID.toString())
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
                .subject(AUTH_USER_ID.toString())
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

    private static CustomerResponse customerResponse() {
        return customerResponse(CUSTOMER_ID);
    }

    private static CustomerResponse customerResponse(UUID customerId) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new CustomerResponse(
                customerId,
                "Ada Lovelace",
                "ada@example.com",
                "+441234567890",
                CustomerStatus.ACTIVE,
                List.of(),
                now,
                now
        );
    }

    private static JwtEncoder encoder(KeyPair keyPair) {
        return NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .jwkPostProcessor(jwk -> jwk.keyID("customer-security-test-key"))
                .build();
    }

    private static String validToken(
            JwtEncoder encoder,
            String issuer,
            List<String> audience,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(AUTH_USER_ID.toString())
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("customer-security-test-jti")
                .claim("roles", List.of("USER"))
                .build();

        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("customer-security-test-key")
                .build();

        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private static String adminToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(AUTH_USER_ID.toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .id("customer-security-admin-test-jti")
                .claim("roles", List.of("ADMIN"))
                .build();

        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("customer-security-test-key")
                .build();

        return JWT_ENCODER.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
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
                Path directory = Files.createTempDirectory("customer-security-test-keys-");
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
