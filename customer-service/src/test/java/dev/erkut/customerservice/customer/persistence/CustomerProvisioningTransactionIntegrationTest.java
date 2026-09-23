package dev.erkut.customerservice.customer.persistence;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.customer.domain.exception.CustomerEmailAlreadyExistsException;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class CustomerProvisioningTransactionIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-05T12:30:15.123456Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CustomerService customerService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void customerFailureRollsBackInboxRegistration() {
        UUID existingAuthUserId = UUID.randomUUID();
        UUID failedAuthUserId = UUID.randomUUID();
        String email = "rollback-" + UUID.randomUUID() + "@example.com";

        provision(existingAuthUserId, UUID.randomUUID(), email);

        assertThrows(CustomerEmailAlreadyExistsException.class, () ->
                provision(failedAuthUserId, UUID.randomUUID(), email));

        assertEquals(1, countInboxMessages());
        assertEquals(0, countCustomer(failedAuthUserId));
    }

    private void provision(UUID authUserId, UUID messageId, String email) {
        transactionTemplate.executeWithoutResult(status -> customerService.handleCreateCustomerCommand(
                new MessageEnvelope(messageId, "CREATE_CUSTOMER_COMMAND", OCCURRED_AT, null),
                new CreateCustomerCommand(authUserId, email)
        ));
    }

    private int countInboxMessages() {
        return jdbcTemplate.queryForObject("select count(*) from inbox_messages", Integer.class);
    }

    private int countCustomer(UUID authUserId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from customers where auth_user_id = ?", Integer.class, authUserId);
    }
}
