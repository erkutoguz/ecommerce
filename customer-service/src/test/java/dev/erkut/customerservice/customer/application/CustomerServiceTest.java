package dev.erkut.customerservice.customer.application;

import dev.erkut.customerservice.customer.api.request.CustomerAddressCreateRequest;
import dev.erkut.customerservice.customer.api.internal.CustomerLookupResponse;
import dev.erkut.customerservice.customer.api.response.CustomerAddressResponse;
import dev.erkut.customerservice.customer.domain.exception.CustomerNotFoundException;
import dev.erkut.customerservice.customer.domain.exception.InvalidCustomerStateException;
import dev.erkut.customerservice.customer.domain.Customer;
import dev.erkut.customerservice.customer.domain.CustomerStatus;
import dev.erkut.customerservice.customer.persistence.CustomerRepository;
import dev.erkut.customerservice.inbox.application.InboxService;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final UUID AUTH_USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID MESSAGE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InboxService inboxService;

    private CustomerService customerService() {
        return new CustomerService(customerRepository, inboxService);
    }

    @Test
    void provisioningCreatesCustomerWithAuthIdentityAndNormalizedEmail() {
        MessageEnvelope envelope = envelope(MESSAGE_ID);
        CreateCustomerCommand command = new CreateCustomerCommand(AUTH_USER_ID, " ADA@EXAMPLE.COM ");
        when(inboxService.tryRegister(eq(MESSAGE_ID), eq("CREATE_CUSTOMER_COMMAND"), eq(AUTH_USER_ID), any()))
                .thenReturn(true);
        when(customerRepository.existsByAuthUserId(AUTH_USER_ID)).thenReturn(false);
        when(customerRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(customerService().handleCreateCustomerCommand(envelope, command));

        var captor = org.mockito.ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertEquals(AUTH_USER_ID, captor.getValue().getAuthUserId());
        assertEquals("ada@example.com", captor.getValue().getEmail());
        assertEquals(CustomerStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getName());
        assertEquals(null, captor.getValue().getPhone());
        verify(customerRepository).existsByEmail("ada@example.com");
    }

    @Test
    void duplicateEmailErrorDoesNotIncludeEmail() {
        MessageEnvelope envelope = envelope(MESSAGE_ID);
        when(inboxService.tryRegister(eq(MESSAGE_ID), eq("CREATE_CUSTOMER_COMMAND"), eq(AUTH_USER_ID), any()))
                .thenReturn(true);
        when(customerRepository.existsByEmail("ada@example.com")).thenReturn(true);

        var exception = assertThrows(
                dev.erkut.customerservice.customer.domain.exception.CustomerEmailAlreadyExistsException.class,
                () -> customerService().handleCreateCustomerCommand(
                        envelope, new CreateCustomerCommand(AUTH_USER_ID, "ada@example.com")));

        assertEquals("Customer already exists with email", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void duplicateMessageIdIsNoOp() {
        MessageEnvelope envelope = envelope(MESSAGE_ID);
        when(inboxService.tryRegister(eq(MESSAGE_ID), eq("CREATE_CUSTOMER_COMMAND"), eq(AUTH_USER_ID), any()))
                .thenReturn(false);

        assertFalse(customerService().handleCreateCustomerCommand(
                envelope, new CreateCustomerCommand(AUTH_USER_ID, "ada@example.com")));

        verify(inboxService).tryRegister(eq(MESSAGE_ID), eq("CREATE_CUSTOMER_COMMAND"), eq(AUTH_USER_ID), any());
        verify(customerRepository, never()).existsByAuthUserId(any());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void existingAuthUserProvisioningIsNoOpForDifferentMessageId() {
        UUID secondMessageId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        when(inboxService.tryRegister(eq(secondMessageId), eq("CREATE_CUSTOMER_COMMAND"), eq(AUTH_USER_ID), any()))
                .thenReturn(true);
        when(customerRepository.existsByAuthUserId(AUTH_USER_ID)).thenReturn(true);

        assertFalse(customerService().handleCreateCustomerCommand(
                envelope(secondMessageId), new CreateCustomerCommand(AUTH_USER_ID, "ada@example.com")));

        verify(customerRepository).existsByAuthUserId(AUTH_USER_ID);
        verify(customerRepository, never()).existsByEmail(any());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void getCustomerByIdReturnsMappedCustomerOrThrowsWhenMissing() {
        Customer customer = customer("Ada Lovelace", "ada@example.com");
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, AUTH_USER_ID)).thenReturn(Optional.of(customer));

        assertEquals("ada@example.com", customerService().getCustomerById(CUSTOMER_ID, AUTH_USER_ID).email());

        when(customerRepository.findByIdAndAuthUserId(OTHER_CUSTOMER_ID, AUTH_USER_ID)).thenReturn(Optional.empty());
        assertThrows(CustomerNotFoundException.class,
                () -> customerService().getCustomerById(OTHER_CUSTOMER_ID, AUTH_USER_ID));
    }

    @Test
    void getCustomerByIdDoesNotReturnCustomerForAnotherAuthUser() {
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, OTHER_CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class,
                () -> customerService().getCustomerById(CUSTOMER_ID, OTHER_CUSTOMER_ID));
    }

    @Test
    void getByAuthUserIdReturnsCustomerIdAndStatusWithoutUsingAuthUserIdAsCustomerId() throws Exception {
        Customer customer = customer("Ada Lovelace", "ada@example.com");
        setCustomerId(customer, CUSTOMER_ID);
        when(customerRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.of(customer));

        CustomerLookupResponse response = customerService().getByAuthUserId(AUTH_USER_ID);

        assertEquals(CUSTOMER_ID, response.customerId());
        assertEquals(CustomerStatus.ACTIVE, response.status());
        verify(customerRepository).findByAuthUserId(AUTH_USER_ID);
    }

    @Test
    void getByAuthUserIdThrowsWhenAuthUserIdIsUnknown() {
        when(customerRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());

        CustomerNotFoundException exception = assertThrows(CustomerNotFoundException.class,
                () -> customerService().getByAuthUserId(AUTH_USER_ID));
        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    void getCustomersMapsPageAndUsesCreatedAtAndIdDescendingSort() {
        Customer first = customer("Ada", "ada@example.com");
        Customer second = customer("Grace", "grace@example.com");
        when(customerRepository.findByAuthUserId(eq(AUTH_USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        var response = customerService().getCustomers(2, 25, AUTH_USER_ID);

        assertEquals(2, response.getTotalElements());
        assertEquals("ada@example.com", response.getContent().getFirst().email());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findByAuthUserId(eq(AUTH_USER_ID), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(25, pageable.getValue().getPageSize());
        assertEquals(List.of("createdAt", "id"),
                pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList());
        assertTrue(pageable.getValue().getSort().stream().allMatch(order -> order.isDescending()));
    }

    @Test
    void getCustomersForAdminUsesUnfilteredPaginatedRepositoryQuery() {
        Customer first = customer("Ada", "ada@example.com");
        Customer second = customer("Grace", "grace@example.com");
        when(customerRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        var response = customerService().getCustomersForAdmin(1, 25);

        assertEquals(2, response.getTotalElements());
        verify(customerRepository).findAll(any(Pageable.class));
        verify(customerRepository, never()).findByAuthUserId(any(UUID.class), any(Pageable.class));
    }

    @Test
    void adminCustomerReadDoesNotRequireAuthUserOwnership() throws Exception {
        Customer customer = customer("Ada", "ada@example.com");
        setCustomerId(customer, OTHER_CUSTOMER_ID);
        when(customerRepository.findById(OTHER_CUSTOMER_ID)).thenReturn(Optional.of(customer));

        var response = customerService().getCustomerByIdForAdmin(OTHER_CUSTOMER_ID);

        assertEquals(OTHER_CUSTOMER_ID, response.customerId());
        verify(customerRepository).findById(OTHER_CUSTOMER_ID);
        verify(customerRepository, never()).findByAuthUserId(any(UUID.class));
    }

    @Test
    void adminCustomerReadUnknownCustomerThrowsNotFound() {
        when(customerRepository.findById(OTHER_CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class,
                () -> customerService().getCustomerByIdForAdmin(OTHER_CUSTOMER_ID));
    }

    @Test
    void adminDeactivationReusesCustomerDomainTransitionWithoutOwnershipLookup() throws Exception {
        Customer customer = customer("Ada", "ada@example.com");
        setCustomerId(customer, OTHER_CUSTOMER_ID);
        when(customerRepository.findById(OTHER_CUSTOMER_ID)).thenReturn(Optional.of(customer));

        var response = customerService().deactivateCustomerForAdmin(OTHER_CUSTOMER_ID);

        assertEquals(CustomerStatus.INACTIVE, response.status());
        verify(customerRepository).findById(OTHER_CUSTOMER_ID);
        verify(customerRepository, never()).findByIdAndAuthUserId(any(UUID.class), any(UUID.class));
    }

    @Test
    void allCustomerMutationsRequireTheAuthenticatedCustomer() {
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, OTHER_CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class,
                () -> customerService().deactivateCustomer(CUSTOMER_ID, OTHER_CUSTOMER_ID));
        assertThrows(CustomerNotFoundException.class,
                () -> customerService().addCustomerAddress(
                        CUSTOMER_ID,
                        OTHER_CUSTOMER_ID,
                        new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom")));
        assertThrows(CustomerNotFoundException.class,
                () -> customerService().removeCustomerAddress(
                        CUSTOMER_ID,
                        OTHER_CUSTOMER_ID,
                        UUID.randomUUID()));

        verify(customerRepository, never()).flush();
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void deactivateCustomerMutatesManagedCustomerWithoutSavingAgain() {
        Customer customer = customer("Ada", "ada@example.com");
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, AUTH_USER_ID)).thenReturn(Optional.of(customer));

        var response = customerService().deactivateCustomer(CUSTOMER_ID, AUTH_USER_ID);

        assertEquals(CustomerStatus.INACTIVE, response.status());
        assertEquals(CustomerStatus.INACTIVE, customer.getStatus());
        assertTrue(response.updatedAt().isAfter(CREATED_AT));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void addCustomerAddressLoadsAggregateFlushesAndReturnsAddressResponseWithoutSavingCustomer() {
        Customer customer = customer("Ada", "ada@example.com");
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, AUTH_USER_ID)).thenReturn(Optional.of(customer));

        CustomerAddressResponse response = customerService().addCustomerAddress(
                CUSTOMER_ID, AUTH_USER_ID,
                new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom"));

        assertEquals("1 Main Street", response.fullAddress());
        assertEquals("London", response.city());
        assertEquals(1, customer.getAddresses().size());

        InOrder order = inOrder(customerRepository);
        order.verify(customerRepository).findByIdAndAuthUserId(CUSTOMER_ID, AUTH_USER_ID);
        order.verify(customerRepository).flush();
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void removeCustomerAddressUsesAggregateAndDoesNotSaveCustomer() throws Exception {
        Customer customer = customer("Ada", "ada@example.com");
        var address = customer.addAddress("1 Main Street", "London", "United Kingdom", CREATED_AT.plusSeconds(1));
        setAddressId(address, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, AUTH_USER_ID)).thenReturn(Optional.of(customer));

        customerService().removeCustomerAddress(CUSTOMER_ID, AUTH_USER_ID, address.getId());

        assertTrue(customer.getAddresses().isEmpty());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void addressMutationsPropagateInvalidCustomerState() {
        Customer customer = customer("Ada", "ada@example.com");
        customer.deactivateCustomer(CREATED_AT.plusSeconds(1));
        when(customerRepository.findByIdAndAuthUserId(CUSTOMER_ID, AUTH_USER_ID)).thenReturn(Optional.of(customer));

        assertThrows(InvalidCustomerStateException.class, () -> customerService().addCustomerAddress(
                CUSTOMER_ID, AUTH_USER_ID,
                new CustomerAddressCreateRequest("1 Main Street", "London", "United Kingdom")));
        assertThrows(InvalidCustomerStateException.class, () -> customerService().removeCustomerAddress(
                CUSTOMER_ID, AUTH_USER_ID, UUID.randomUUID()));
        verify(customerRepository, never()).flush();
        verify(customerRepository, never()).save(any(Customer.class));
    }

    private static void setAddressId(Object address, UUID id) throws Exception {
        var idField = address.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(address, id);
    }

    private static void setCustomerId(Customer customer, UUID id) throws Exception {
        var idField = Customer.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(customer, id);
    }

    private static Customer customer(String name, String email) {
        return Customer.create(AUTH_USER_ID, email, CREATED_AT);
    }

    private static MessageEnvelope envelope(UUID messageId) {
        return new MessageEnvelope(messageId, "CREATE_CUSTOMER_COMMAND", CREATED_AT, null);
    }
}
