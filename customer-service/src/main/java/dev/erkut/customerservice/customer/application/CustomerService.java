package dev.erkut.customerservice.customer.application;

import dev.erkut.customerservice.customer.api.request.CustomerAddressCreateRequest;
import dev.erkut.customerservice.customer.api.response.CustomerAddressResponse;
import dev.erkut.customerservice.customer.api.response.CustomerResponse;
import dev.erkut.customerservice.customer.domain.exception.CustomerEmailAlreadyExistsException;
import dev.erkut.customerservice.customer.domain.exception.CustomerNotFoundException;
import dev.erkut.customerservice.customer.api.CustomerMapper;
import dev.erkut.customerservice.customer.domain.Customer;
import dev.erkut.customerservice.customer.domain.CustomerAddress;
import dev.erkut.customerservice.inbox.application.InboxService;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
import dev.erkut.customerservice.customer.persistence.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final InboxService inboxService;
    public CustomerService(
            CustomerRepository customerRepository,
            InboxService inboxService
    ) {
        this.customerRepository = customerRepository;
        this.inboxService = inboxService;
    }

    @Transactional
    public void handleCreateCustomerCommand(MessageEnvelope envelope, CreateCustomerCommand command) {
        validateCustomerCommand(envelope, command);
        Instant now = Instant.now();

        if (isDuplicate(envelope, command.authUserId(), now)) {
            return;
        }

        if (customerRepository.existsByAuthUserId(command.authUserId())) {
            return;
        }

        Customer customer = Customer.create(command.authUserId(), command.email(), now);

        if(customerRepository.existsByEmail(customer.getEmail())) {
            throw new CustomerEmailAlreadyExistsException("Customer already exists with email: " + customer.getEmail());
        }

        customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + customerId));
        return CustomerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending()
                .and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Customer> customers = customerRepository.findAll(pageable);
        return customers.map(CustomerMapper::toResponse);
    }

    @Transactional
    public CustomerResponse deactivateCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + customerId));
        Instant now = Instant.now();
        customer.deactivateCustomer(now);
        return CustomerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerAddressResponse addCustomerAddress(UUID customerId, CustomerAddressCreateRequest req) {
        Instant now = Instant.now();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + customerId));

        CustomerAddress address = customer.addAddress(req.fullAddress(), req.city(), req.country(), now);
        customerRepository.flush();
        return CustomerMapper.toResponse(address);
    }

    @Transactional
    public void removeCustomerAddress(UUID customerId, UUID addressId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + customerId));
        customer.removeAddress(addressId, Instant.now());
    }

    private void validateCustomerCommand(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Customer command cannot be null");
        }
    }

    private boolean isDuplicate(
            MessageEnvelope envelope,
            UUID authUserId,
            Instant now
    ) {
        return !inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                authUserId,
                now
        );
    }
}
