package dev.erkut.productservice.security;

import dev.erkut.productservice.product.api.ProductController;
import dev.erkut.productservice.product.api.error.GlobalExceptionHandler;
import dev.erkut.productservice.product.api.response.ProductResponse;
import dev.erkut.productservice.product.application.ProductService;
import dev.erkut.productservice.product.domain.ProductStatus;
import dev.erkut.productservice.security.token.JwtConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
@EnableWebSecurity
class ProductSecurityTest {

    private static final String ISSUER = "ecommerce-auth";
    private static final String AUDIENCE = "ecommerce-api";
    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final TestKeyFiles TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder JWT_ENCODER = encoder(TEST_KEYS.keyPair());
    private static final TestKeyFiles WRONG_TEST_KEYS = TestKeyFiles.create();
    private static final JwtEncoder WRONG_KEY_ENCODER = encoder(WRONG_TEST_KEYS.keyPair());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private ProductService productService;

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
    void anonymousProductListIsPublic() throws Exception {
        when(productService.getProducts(0, 10))
                .thenReturn(new PageImpl<>(List.of(productResponse())));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousProductReadIsPublic() throws Exception {
        when(productService.getProductWithId(PRODUCT_ID)).thenReturn(productResponse());

        mockMvc.perform(get("/products/{productId}", PRODUCT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousBulkLookupRemainsCompatible() throws Exception {
        when(productService.bulkLookup(any()))
                .thenReturn(List.of(productResponse()));

        mockMvc.perform(post("/products/bulk-lookup")
                        .contentType("application/json")
                        .content("{\"requestedProductIds\":[\"" + PRODUCT_ID + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousMutationsReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType("application/json")
                        .content("{\"name\":\"Keyboard\",\"price\":99.90}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType("application/json")
                        .content("{\"name\":\"New Keyboard\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/products/{productId}/deactivate", PRODUCT_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/products/{productId}", PRODUCT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userMutationsReturnForbidden() throws Exception {
        String token = bearerToken(userToken());

        mockMvc.perform(post("/products")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"Keyboard\",\"price\":99.90}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"New Keyboard\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/products/{productId}/deactivate", PRODUCT_ID)
                        .header("Authorization", token))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/products/{productId}", PRODUCT_ID)
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachAllProductMutations() throws Exception {
        when(productService.createProduct(any())).thenReturn(productResponse());
        when(productService.updateProduct(any(), any())).thenReturn(productResponse());
        when(productService.deactivateProduct(any())).thenReturn(productResponse());

        String token = bearerToken(adminToken());

        mockMvc.perform(post("/products")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"Keyboard\",\"price\":99.90}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"name\":\"New Keyboard\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/products/{productId}/deactivate", PRODUCT_ID)
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/products/{productId}", PRODUCT_ID)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", bearerToken(token(
                                WRONG_KEY_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                List.of("ADMIN"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();

        mockMvc.perform(post("/products")
                        .header("Authorization", bearerToken(token(
                                JWT_ENCODER,
                                ISSUER,
                                List.of(AUDIENCE),
                                List.of("ADMIN"),
                                now.minus(10, ChronoUnit.MINUTES),
                                now.minus(5, ChronoUnit.MINUTES)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", bearerToken(token(
                                JWT_ENCODER,
                                "wrong-issuer",
                                List.of(AUDIENCE),
                                List.of("ADMIN"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", bearerToken(token(
                                JWT_ENCODER,
                                ISSUER,
                                List.of("wrong-audience"),
                                List.of("ADMIN"),
                                Instant.now().minusSeconds(30),
                                Instant.now().plusSeconds(300)))))
                .andExpect(status().isUnauthorized());
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

    private static ProductResponse productResponse() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new ProductResponse(
                PRODUCT_ID,
                "Keyboard",
                new BigDecimal("99.90"),
                ProductStatus.ACTIVE,
                now,
                now
        );
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

    private static String adminToken() {
        return token(
                JWT_ENCODER,
                ISSUER,
                List.of(AUDIENCE),
                List.of("ADMIN"),
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
                .id("product-security-test-jti")
                .claim("roles", roles)
                .build();

        JwsHeader headers = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId("product-security-test-key")
                .build();

        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private static String bearerToken(String token) {
        return "Bearer " + token;
    }

    private static JwtEncoder encoder(KeyPair keyPair) {
        return NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .jwkPostProcessor(jwk -> jwk.keyID("product-security-test-key"))
                .build();
    }

    private record TestKeyFiles(Path directory, Path publicKey, KeyPair keyPair) {
        static TestKeyFiles create() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                Path directory = Files.createTempDirectory("product-security-test-keys-");
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
