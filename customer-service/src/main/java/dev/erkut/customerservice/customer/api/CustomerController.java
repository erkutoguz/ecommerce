package dev.erkut.customerservice.customer.api;

import dev.erkut.customerservice.customer.api.request.CustomerAddressCreateRequest;
import dev.erkut.customerservice.customer.api.response.CustomerAddressResponse;
import dev.erkut.customerservice.customer.api.request.CustomerCreateRequest;
import dev.erkut.customerservice.customer.api.response.CustomerResponse;
import dev.erkut.customerservice.customer.application.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
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

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable("customerId") UUID customerId) {
        CustomerResponse response = customerService.getCustomerById(customerId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getCustomers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Page<CustomerResponse> response = customerService.getCustomers(page, size);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{customerId}/deactivate")
    public ResponseEntity<CustomerResponse> deactivateCustomer(@PathVariable("customerId") UUID customerId) {
        CustomerResponse response = customerService.deactivateCustomer(customerId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{customerId}/addresses")
    public ResponseEntity<CustomerAddressResponse> addCustomerAddress(
            @PathVariable("customerId") UUID customerId,
            @Valid @RequestBody CustomerAddressCreateRequest req
    ) {
      CustomerAddressResponse response = customerService.addCustomerAddress(customerId, req);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{customerId}/addresses/{addressId}")
    public ResponseEntity<Void> removeCustomerAddress(
            @PathVariable("customerId") UUID customerId,
            @PathVariable("addressId") UUID addressId
    ) {
        customerService.removeCustomerAddress(customerId, addressId);
        return ResponseEntity.noContent().build();
    }
}
