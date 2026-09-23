package dev.erkut.authservice;

import dev.erkut.authservice.authentication.AuthService;
import dev.erkut.authservice.authentication.dto.RegisterRequest;
import dev.erkut.authservice.outbox.application.OutboxService;
import dev.erkut.authservice.user.AuthUserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class AuthRegistrationTransactionTest {

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
    private AuthService authService;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxService outboxService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from outbox_messages");
        authUserRepository.deleteAll();
        authUserRepository.flush();
    }

    @AfterAll
    static void deleteTestKeyFiles() throws IOException {
        TEST_KEYS.delete();
    }

    @Test
    void outboxFailureRollsBackAuthUser() {
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService).createCustomerCommand(any(), any());

        assertThrows(IllegalStateException.class, () -> authService.register(
                new RegisterRequest("rollback@example.com", "correct-password")));

        assertEquals(0, authUserRepository.count());
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from outbox_messages", Integer.class));
    }

    private record TestKeyFiles(Path directory, Path publicKey, Path privateKey) {
        static TestKeyFiles create() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair keyPair = generator.generateKeyPair();
                Path directory = Files.createTempDirectory("auth-service-transaction-test-keys-");
                Path publicKey = directory.resolve("public.pem");
                Path privateKey = directory.resolve("private.pem");
                Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
                Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
                return new TestKeyFiles(directory, publicKey, privateKey);
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
