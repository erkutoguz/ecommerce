package dev.erkut.authservice;

import dev.erkut.authservice.token.JwtProperties;
import dev.erkut.authservice.user.AuthUser;
import dev.erkut.authservice.user.AuthUserRepository;
import dev.erkut.authservice.user.Role;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthServiceApplicationTests {

    private static final String REGISTER_EMAIL = "registered@example.com";
    private static final String REGISTER_PASSWORD = "correct-password";
    private static final String CONCURRENT_EMAIL = "concurrent@example.com";
    private static final String CONCURRENT_PASSWORD = "concurrent-password";
    private static final TestKeyFiles TEST_KEYS = TestKeyFiles.create();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.public-key", () -> TEST_KEYS.publicKey().toUri().toString());
        registry.add("auth.jwt.private-key", () -> TEST_KEYS.privateKey().toUri().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanDatabase() {
        authUserRepository.deleteAll();
        authUserRepository.flush();
    }

    @AfterAll
    static void deleteTestKeyFiles() throws IOException {
        TEST_KEYS.delete();
    }

    @Test
    void flywayCreatesAuthSchemaInPostgres() {
        assertEquals("auth_users", jdbcTemplate.queryForObject(
                "select to_regclass('public.auth_users')", String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from information_schema.table_constraints "
                        + "where table_name = 'auth_users' and constraint_name = 'uq_auth_users_email'",
                Integer.class));
    }

    @Test
    void successfulRegistrationPersistsUserAndReturnsBearerResponse() throws Exception {
        MvcResult result = performRegister(REGISTER_EMAIL, REGISTER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.accessTokenTtl().toSeconds()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        AuthUser user = authUserRepository.findByEmail(REGISTER_EMAIL).orElseThrow();
        assertNotNull(user.getId());
        assertEquals(Role.USER, user.getRole());
        assertTrue(user.isEnabled());
        assertNotEquals(REGISTER_PASSWORD, user.getPasswordHash());
        assertTrue(passwordEncoder.matches(REGISTER_PASSWORD, user.getPasswordHash()));
        assertFalse(result.getResponse().getContentAsString().contains(user.getPasswordHash()));
    }

    @Test
    void registrationNormalizesEmailAndLoginUsesTheSameNormalization() throws Exception {
        performRegister("  TEST.User@Example.COM  ", REGISTER_PASSWORD)
                .andExpect(status().isOk());

        assertTrue(authUserRepository.findByEmail("test.user@example.com").isPresent());

        performLogin("  tEsT.uSeR@eXaMpLe.cOm  ", REGISTER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void duplicateNormalizedEmailReturnsConflictAndDoesNotCreateAnotherUser() throws Exception {
        performRegister("Test@Example.com", REGISTER_PASSWORD)
                .andExpect(status().isOk());

        MvcResult duplicate = performRegister("test@example.COM", REGISTER_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User with email test@example.com already exists"))
                .andReturn();

        assertEquals(1, authUserRepository.count());
        assertFalse(duplicate.getResponse().getContentAsString().contains("password"));
    }

    @Test
    void concurrentRegistrationsForTheSameEmailAreSerializedByPostgresUniqueConstraint() throws Exception {
        int requestCount = 4;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<java.util.concurrent.Future<Integer>> futures = IntStream.range(0, requestCount)
                    .mapToObj(attempt -> executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return performRegister(
                                attempt % 2 == 0
                                        ? "Concurrent@Example.com"
                                        : "concurrent@example.COM",
                                CONCURRENT_PASSWORD
                        ).andReturn().getResponse().getStatus();
                    }))
                    .toList();

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(30, TimeUnit.SECONDS);
                        } catch (Exception exception) {
                            throw new AssertionError("Concurrent registration request failed", exception);
                        }
                    })
                    .toList();

            long successfulRequests = statuses.stream().filter(status -> status == 200).count();
            long conflictRequests = statuses.stream().filter(status -> status == 409).count();
            assertEquals(1, successfulRequests, "exactly one registration must win");
            assertEquals(requestCount - 1, conflictRequests, "all losing registrations must be conflicts");
            assertEquals(1, authUserRepository.count());
            assertTrue(authUserRepository.findByEmail(CONCURRENT_EMAIL).isPresent());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void successfulLoginReturnsBearerTokenForCorrectCredentials() throws Exception {
        performRegister(REGISTER_EMAIL, REGISTER_PASSWORD).andExpect(status().isOk());

        performLogin(REGISTER_EMAIL, REGISTER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.accessTokenTtl().toSeconds()));
    }

    @Test
    void invalidPasswordReturnsUnauthorizedWithoutCredentialDetails() throws Exception {
        performRegister(REGISTER_EMAIL, REGISTER_PASSWORD).andExpect(status().isOk());

        MvcResult result = performLogin(REGISTER_EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("passwordHash"));
    }

    @Test
    void unknownEmailReturnsSameUnauthorizedCredentialError() throws Exception {
        MvcResult result = performLogin("unknown@example.com", "arbitrary-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("unknown@example.com"));
        assertFalse(result.getResponse().getContentAsString().contains("arbitrary-password"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        performRegister(REGISTER_EMAIL, REGISTER_PASSWORD).andExpect(status().isOk());
        jdbcTemplate.update("update auth_users set enabled = false where email = ?", REGISTER_EMAIL);

        performLogin(REGISTER_EMAIL, REGISTER_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void invalidRegistrationEmailReturnsBadRequest() throws Exception {
        performRegister("not-an-email", REGISTER_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void shortRegistrationPasswordReturnsBadRequest() throws Exception {
        performRegister(REGISTER_EMAIL, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void invalidLoginRequestReturnsBadRequest() throws Exception {
        performLogin("not-an-email", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void issuedJwtIsCryptographicallyValidAndContainsExpectedClaims() throws Exception {
        MvcResult result = performRegister(REGISTER_EMAIL, REGISTER_PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        AuthUser user = authUserRepository.findByEmail(REGISTER_EMAIL).orElseThrow();

        JwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(TEST_KEYS.publicKeyObject())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        Jwt jwt = decoder.decode(accessTokenFrom(result));

        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals(jwtProperties.issuer(), jwt.getClaimAsString("iss"));
        assertTrue(jwt.getAudience().contains(jwtProperties.audience()));
        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals(List.of(Role.USER.name()), jwt.getClaimAsStringList("roles"));
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertNotNull(jwt.getId());
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
        assertTrue(Math.abs(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds()
                - jwtProperties.accessTokenTtl().toSeconds()) <= 2);
    }

    private ResultActions performRegister(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(APPLICATION_JSON)
                .content(requestJson(email, password)));
    }

    private ResultActions performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(APPLICATION_JSON)
                .content(requestJson(email, password)));
    }

    private static String requestJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String accessTokenFrom(MvcResult result) throws IOException {
        return new JsonMapper()
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asString();
    }

    private record TestKeyFiles(Path directory, Path publicKey, Path privateKey,
                                java.security.interfaces.RSAPublicKey publicKeyObject) {
        static TestKeyFiles create() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                Path directory = Files.createTempDirectory("auth-service-test-keys-");
                Path publicKey = directory.resolve("public.pem");
                Path privateKey = directory.resolve("private.pem");
                Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
                Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
                return new TestKeyFiles(directory, publicKey, privateKey,
                        (java.security.interfaces.RSAPublicKey) keyPair.getPublic());
            } catch (Exception exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        void delete() throws IOException {
            Files.deleteIfExists(publicKey);
            Files.deleteIfExists(privateKey);
            Files.deleteIfExists(directory);
        }

        private static String pem(String type, byte[] encoded) {
            return "-----BEGIN " + type + "-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                    + "\n-----END " + type + "-----\n";
        }
    }

}
