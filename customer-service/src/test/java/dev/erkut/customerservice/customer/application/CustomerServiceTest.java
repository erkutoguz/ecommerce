package dev.erkut.customerservice.customer.application;

import dev.erkut.customerservice.customer.api.request.CustomerAddressCreateRequest;
import dev.erkut.customerservice.customer.api.request.CustomerCreateRequest;
import dev.erkut.customerservice.customer.api.response.CustomerAddressResponse;
import dev.erkut.customerservice.customer.domain.exception.CustomerEmailAlreadyExistsException;
import dev.erkut.customerservice.customer.domain.exception.CustomerNotFoundException;
import dev.erkut.customerservice.customer.domain.exception.InvalidCustomerStateException;
import dev.erkut.customerservice.customer.domain.Customer;
import dev.erkut.customerservice.customer.domain.CustomerStatus;
import dev.erkut.customerservice.customer.persistence.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private CustomerRepository customerRepository;

    private CustomerService customerService() {
        return new CustomerService(customerRepository);
    }

    @Test
    void createCustomerNormalizesEmailAndSavesNewCustomer() {
        CustomerCreateRequest request = new CustomerCreateRequest("Ada Lovelace", " ADA@EXAMPLE.COM ", null);
        when(customerRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = customerService().createCustomer(request);

        assertEquals("ada@example.com", response.email());
        assertEquals(CustomerStatus.ACTIVE, response.status());
        verify(customerRepository).existsByEmail("ada@example.com");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void createCustomerDuplicateNormalizedEmailThrowsAndDoesNotSave() {
        when(customerRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThrows(CustomerEmailAlreadyExistsException.class, () -> customerService().createCustomer(
                new CustomerCreateRequest("Ada Lovelace", "ADA@example.com", null)));

        verify(customerRepository).existsByEmail("ada@example.com");
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void getCustomerByIdReturnsMappedCustomerOrThrowsWhenMissing() {
        Customer customer = Customer.create("Ada Lovelace", "ada@example.com", null, CREATED_AT);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        assertEquals("ada@example.com", customerService().getCustomerById(CUSTOMER_ID).email());

        when(customerRepository.findById(OTHER_CUSTOMER_ID)).thenReturn(Optional.empty());
        assertThrows(CustomerNotFoundException.class,
                () -> customerService().getCustomerById(OTHER_CUSTOMER_ID));
    }

    @Test
    void getCustomersMapsPageAndUsesCreatedAtAndIdDescendingSort() {
        Customer first = Customer.create("Ada", "ada@example.com", null, CREATED_AT);
        Customer second = Customer.create("Grace", "grace@example.com", null, CREATED_AT);
        when(customerRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        var response = customerService().getCustomers(2, 25);

        assertEquals(2, response.getTotalElements());
        assertEquals("ada@example.com", response.getContent().getFirst().email());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findAll(pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(25, pageable.getValue().getPageSize());
        assertEquals(List.of("createdAt", "id"),
                pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList());
        assertTrue(pageable.getValue().getSort().stream().allMatch(order -> order.isDescending()));
    }

    @Test
    void deactivateCustomerMutatesManagedCustomerWithoutSavingAgain() {
        Customer customer = Customer.create("Ada", "ada@example.com", null, CREATED_AT);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        var response = customerService().deactivateCustomer(CUSTOMER_ID);

        assertEquals(CustomerStatus.INACTIVE, response.status());
        assertEquals(CustomerStatus.INACTIVE, customer.getStatus());
        assertTrue(response.updatedAt().isAfter(CREATED_AT));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void addCustomerAddressLoadsAggregateFlushesAndReturnsAddressResponseWithoutSavingCustomer() {
        Customer customer = Customer.create("Ada", "ada@example.com", null, CREATED_AT);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        CustomerAddressResponse response = customerService().addCustomerAddress(
                CUSTOMER_ID, new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom"));

        assertEquals("1 Main Street", response.fullAddress());
        assertEquals("London", response.city());
        assertEquals(1, customer.getAddresses().size());

        InOrder order = inOrder(customerRepository);
        order.verify(customerRepository).findById(CUSTOMER_ID);
        order.verify(customerRepository).flush();
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void removeCustomerAddressUsesAggregateAndDoesNotSaveCustomer() throws Exception {
        Customer customer = Customer.create("Ada", "ada@example.com", null, CREATED_AT);
        var address = customer.addAddress("1 Main Street", "London", "United Kingdom", CREATED_AT.plusSeconds(1));
        setAddressId(address, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        customerService().removeCustomerAddress(CUSTOMER_ID, address.getId());

        assertTrue(customer.getAddresses().isEmpty());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void addressMutationsPropagateInvalidCustomerState() {
        Customer customer = Customer.create("Ada", "ada@example.com", null, CREATED_AT);
        customer.deactivateCustomer(CREATED_AT.plusSeconds(1));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        assertThrows(InvalidCustomerStateException.class, () -> customerService().addCustomerAddress(
                CUSTOMER_ID, new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom")));
        assertThrows(InvalidCustomerStateException.class, () -> customerService().removeCustomerAddress(
                CUSTOMER_ID, UUID.randomUUID()));
        verify(customerRepository, never()).flush();
        verify(customerRepository, never()).save(any(Customer.class));
    }

    private static void setAddressId(Object address, UUID id) throws Exception {
        var idField = address.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(address, id);
    }
}
