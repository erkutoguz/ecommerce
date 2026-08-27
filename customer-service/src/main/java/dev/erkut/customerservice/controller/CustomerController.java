package dev.erkut.customerservice.controller;

import dev.erkut.customerservice.dto.CustomerAddressCreateRequest;
import dev.erkut.customerservice.dto.CustomerCreateRequest;
import dev.erkut.customerservice.dto.CustomerResponse;
import dev.erkut.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerCreateRequest req) {
        CustomerResponse response = customerService.createCustomer(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{customerId}/addresses")
    public ResponseEntity<CustomerResponse> addCustomerAddress(
            @PathVariable("customerId") UUID customerId,
            @Valid @RequestBody CustomerAddressCreateRequest req
    ) {
      CustomerResponse response = customerService.addCustomerAddress(customerId, req);
      return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
