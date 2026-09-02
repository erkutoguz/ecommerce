package dev.erkut.customerservice.customer.domain;

import dev.erkut.customerservice.customer.domain.exception.AddressNotFoundException;
import dev.erkut.customerservice.customer.domain.exception.InvalidCustomerStateException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomerDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createNormalizesEmailAndStartsActive() {
        Customer customer = Customer.create("Ada Lovelace", "  ADA@example.com ", null, CREATED_AT);

        assertEquals("ada@example.com", customer.getEmail());
        assertEquals(CustomerStatus.ACTIVE, customer.getStatus());
        assertEquals(CREATED_AT, customer.getCreatedAt());
        assertEquals(CREATED_AT, customer.getUpdatedAt());
    }

    @Test
    void addAddressAddsChildAndUpdatesTimestamp() {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);
        Instant updatedAt = CREATED_AT.plusSeconds(1);

        var address = customer.addAddress("1 Main Street", "London", "United Kingdom", updatedAt);

        assertEquals(1, customer.getAddresses().size());
        assertSame(address, customer.getAddresses().getFirst());
        assertSame(customer, address.getCustomer());
        assertEquals("1 Main Street", address.getFullAddress());
        assertEquals(updatedAt, customer.getUpdatedAt());
    }

    @Test
    void removeAddressRemovesRequestedChildAndUpdatesTimestamp() throws Exception {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);
        var address = customer.addAddress(
                "1 Main Street", "London", "United Kingdom", CREATED_AT.plusSeconds(1));
        UUID addressId = UUID.randomUUID();
        setAddressId(address, addressId);
        Instant updatedAt = CREATED_AT.plusSeconds(2);

        customer.removeAddress(addressId, updatedAt);

        assertTrue(customer.getAddresses().isEmpty());
        assertEquals(updatedAt, customer.getUpdatedAt());
    }

    @Test
    void returnedAddressesCollectionCannotBeExternallyMutated() {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);
        customer.addAddress("1 Main Street", "London", "United Kingdom", CREATED_AT.plusSeconds(1));

        assertThrows(UnsupportedOperationException.class, () -> customer.getAddresses().clear());
        assertEquals(1, customer.getAddresses().size());
    }

    @Test
    void inactiveCustomerCannotAddOrRemoveAddress() throws Exception {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);
        var address = customer.addAddress(
                "1 Main Street", "London", "United Kingdom", CREATED_AT.plusSeconds(1));
        setAddressId(address, UUID.randomUUID());
        customer.deactivateCustomer(CREATED_AT.plusSeconds(1));

        assertThrows(InvalidCustomerStateException.class, () -> customer.addAddress(
                "1 Main Street", "London", "United Kingdom", CREATED_AT.plusSeconds(2)));
        assertThrows(InvalidCustomerStateException.class, () -> customer.removeAddress(
                address.getId(), CREATED_AT.plusSeconds(2)));
    }

    @Test
    void removeAddressReportsMissingAddress() {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);

        assertThrows(AddressNotFoundException.class, () -> customer.removeAddress(
                UUID.randomUUID(), CREATED_AT.plusSeconds(1)));
    }

    @Test
    void deactivateIsIdempotentAndKeepsFirstTransitionTime() {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);
        Instant deactivatedAt = CREATED_AT.plusSeconds(1);

        customer.deactivateCustomer(deactivatedAt);
        customer.deactivateCustomer(CREATED_AT.plusSeconds(2));

        assertEquals(CustomerStatus.INACTIVE, customer.getStatus());
        assertEquals(deactivatedAt, customer.getUpdatedAt());
    }

    private static void setAddressId(Object address, UUID id) throws Exception {
        Field idField = address.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(address, id);
    }
}
