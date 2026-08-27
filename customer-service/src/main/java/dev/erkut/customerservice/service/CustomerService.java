package dev.erkut.customerservice.service;

import dev.erkut.customerservice.dto.CustomerAddressCreateRequest;
import dev.erkut.customerservice.dto.CustomerCreateRequest;
import dev.erkut.customerservice.dto.CustomerResponse;
import dev.erkut.customerservice.exception.CustomerNotFoundException;
import dev.erkut.customerservice.mapper.CustomerMapper;
import dev.erkut.customerservice.model.Customer;
import dev.erkut.customerservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerCreateRequest req) {
        Instant now = Instant.now();
        Customer customer = Customer.create(req.name(), req.email(), req.phone(), now);

        Customer savedCustomer = customerRepository.save(customer);
        return CustomerMapper.toResponse(savedCustomer);
    }

    @Transactional
    public CustomerResponse addCustomerAddress(UUID customerId, CustomerAddressCreateRequest req) {
        Instant now = Instant.now();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + customerId));

        customer.addAddress(req.fullAddress(), req.city(), req.country(), now);
        return CustomerMapper.toResponse(customer);
    }
}
