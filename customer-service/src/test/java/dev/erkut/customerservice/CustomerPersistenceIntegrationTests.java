package dev.erkut.customerservice;

import dev.erkut.customerservice.dto.CustomerAddressCreateRequest;
import dev.erkut.customerservice.dto.CustomerCreateRequest;
import dev.erkut.customerservice.exception.InvalidCustomerStateException;
import dev.erkut.customerservice.model.CustomerStatus;
import dev.erkut.customerservice.repository.CustomerRepository;
import dev.erkut.customerservice.service.CustomerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class CustomerPersistenceIntegrationTests {

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
        var created = customerService.createCustomer(new CustomerCreateRequest(
                "Dirty Checking Test", uniqueEmail("dirty"), null));
        Instant createdAt = created.createdAt();

        var deactivated = customerService.deactivateCustomer(created.customerId());
        customerRepository.flush();
        entityManager.clear();

        var reloaded = customerRepository.findById(created.customerId()).orElseThrow();
        assertEquals(CustomerStatus.INACTIVE, reloaded.getStatus());
        assertEquals(createdAt, reloaded.getCreatedAt());
        assertEquals(deactivated.updatedAt(), reloaded.getUpdatedAt());
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
