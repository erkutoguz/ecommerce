package dev.erkut.customerservice.customer.persistence;

import dev.erkut.customerservice.customer.api.request.CustomerAddressCreateRequest;
import dev.erkut.customerservice.customer.domain.Customer;
import dev.erkut.customerservice.customer.domain.CustomerStatus;
import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.customer.domain.exception.InvalidCustomerStateException;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@Testcontainers
class CustomerPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-05T12:30:15.123456Z");
    private static final Instant DEACTIVATED_AT = Instant.parse("2026-09-05T12:30:16.654321Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aggregateAddressLifecycleUsesCascadeAndOrphanRemoval() {
        UUID authUserId = UUID.randomUUID();
        String email = uniqueEmail("persistence");
        provisionCustomer(authUserId, UUID.randomUUID(), email);
        var customer = customerRepository.findByAuthUserId(authUserId).orElseThrow();

        var address = customerService.addCustomerAddress(customer.getId(),
                new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom"));
        customerRepository.flush();

        assertNotNull(customer.getId());
        assertNotNull(address.customerAddressId());
        assertEquals(1, countAddresses(customer.getId()));

        customerService.removeCustomerAddress(customer.getId(), address.customerAddressId());
        customerRepository.flush();

        assertEquals(0, countAddresses(customer.getId()));
    }

    @Test
    void dirtyCheckingPersistsDeactivationAndUpdatedAtWithoutExplicitSave() {
        var created = Customer.create(UUID.randomUUID(), uniqueEmail("dirty"), CREATED_AT);
        customerRepository.save(created);
        customerRepository.flush();

        created.deactivateCustomer(DEACTIVATED_AT);
        customerRepository.flush();
        entityManager.clear();

        var reloaded = customerRepository.findById(created.getId()).orElseThrow();
        assertEquals(CustomerStatus.INACTIVE, reloaded.getStatus());
        assertEquals(CREATED_AT, reloaded.getCreatedAt());
        assertEquals(DEACTIVATED_AT, reloaded.getUpdatedAt());
    }

    @Test
    void inactiveCustomerCannotAddOrRemoveAddress() {
        UUID authUserId = UUID.randomUUID();
        provisionCustomer(authUserId, UUID.randomUUID(), uniqueEmail("inactive"));
        var customer = customerRepository.findByAuthUserId(authUserId).orElseThrow();
        var address = customerService.addCustomerAddress(customer.getId(),
                new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom"));
        customerService.deactivateCustomer(customer.getId());

        assertThrows(InvalidCustomerStateException.class, () -> customerService.addCustomerAddress(
                customer.getId(), new CustomerAddressCreateRequest(
                        "2 Main Street", "London", "United Kingdom")));
        assertThrows(InvalidCustomerStateException.class, () -> customerService.removeCustomerAddress(
                customer.getId(), address.customerAddressId()));
    }

    @Test
    void provisioningCreatesInboxAndIndependentCustomerIdentity() {
        UUID authUserId = UUID.randomUUID();
        provisionCustomer(authUserId, UUID.randomUUID(), uniqueEmail("provisioned"));

        var customer = customerRepository.findByAuthUserId(authUserId).orElseThrow();

        assertNotNull(customer.getId());
        assertNotEquals(authUserId, customer.getId());
        assertEquals(authUserId, customer.getAuthUserId());
        assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
        assertEquals(null, customer.getName());
        assertEquals(null, customer.getPhone());
        assertEquals(1, countInboxMessages());
    }

    @Test
    void sameMessageIdIsProcessedOnlyOnce() {
        UUID authUserId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String email = uniqueEmail("duplicate-message");
        provisionCustomer(authUserId, messageId, email);
        provisionCustomer(authUserId, messageId, email);

        assertEquals(1, countCustomersForAuthUser(authUserId));
        assertEquals(1, countInboxMessages());
    }

    @Test
    void differentMessageIdForExistingAuthUserIsNoOp() {
        UUID authUserId = UUID.randomUUID();
        provisionCustomer(authUserId, UUID.randomUUID(), uniqueEmail("duplicate-auth-user"));
        provisionCustomer(authUserId, UUID.randomUUID(), uniqueEmail("duplicate-auth-user-replay"));

        assertEquals(1, countCustomersForAuthUser(authUserId));
        assertEquals(2, countInboxMessages());
    }

    private int countAddresses(UUID customerId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from customer_addresses where customer_id = ?", Integer.class, customerId);
    }

    private int countInboxMessages() {
        return jdbcTemplate.queryForObject("select count(*) from inbox_messages", Integer.class);
    }

    private int countCustomersForAuthUser(UUID authUserId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from customers where auth_user_id = ?", Integer.class, authUserId);
    }

    private void provisionCustomer(UUID authUserId, UUID messageId, String email) {
        customerService.handleCreateCustomerCommand(
                new MessageEnvelope(messageId, "CREATE_CUSTOMER_COMMAND", CREATED_AT, null),
                new CreateCustomerCommand(authUserId, email));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
