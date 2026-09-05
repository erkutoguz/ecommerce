package dev.erkut.customerservice.customer.persistence;

import dev.erkut.customerservice.customer.api.request.CustomerAddressCreateRequest;
import dev.erkut.customerservice.customer.api.request.CustomerCreateRequest;
import dev.erkut.customerservice.customer.domain.Customer;
import dev.erkut.customerservice.customer.domain.CustomerStatus;
import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.customer.domain.exception.InvalidCustomerStateException;
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
        var customer = customerService.createCustomer(new CustomerCreateRequest(
                "Persistence Test", uniqueEmail("persistence"), null));

        var address = customerService.addCustomerAddress(customer.customerId(),
                new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom"));
        customerRepository.flush();

        assertNotNull(customer.customerId());
        assertNotNull(address.customerAddressId());
        assertEquals(1, countAddresses(customer.customerId()));

        customerService.removeCustomerAddress(customer.customerId(), address.customerAddressId());
        customerRepository.flush();

        assertEquals(0, countAddresses(customer.customerId()));
    }

    @Test
    void dirtyCheckingPersistsDeactivationAndUpdatedAtWithoutExplicitSave() {
        var created = Customer.create("Dirty Checking Test", uniqueEmail("dirty"), null, CREATED_AT);
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
        var customer = customerService.createCustomer(new CustomerCreateRequest(
                "Inactive Test", uniqueEmail("inactive"), null));
        var address = customerService.addCustomerAddress(customer.customerId(),
                new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom"));
        customerService.deactivateCustomer(customer.customerId());

        assertThrows(InvalidCustomerStateException.class, () -> customerService.addCustomerAddress(
                customer.customerId(), new CustomerAddressCreateRequest(
                        "2 Main Street", "London", "United Kingdom")));
        assertThrows(InvalidCustomerStateException.class, () -> customerService.removeCustomerAddress(
                customer.customerId(), address.customerAddressId()));
    }

    private int countAddresses(UUID customerId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from customer_addresses where customer_id = ?", Integer.class, customerId);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
