package dev.erkut.customerservice.customer.api.admin;

import dev.erkut.customerservice.customer.api.response.CustomerResponse;
import dev.erkut.customerservice.customer.application.CustomerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/customers")
public class AdminCustomerController {

    private final CustomerService customerService;

    public AdminCustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getCustomers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(customerService.getCustomersForAdmin(page, size));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.getCustomerByIdForAdmin(customerId));
    }

    @PostMapping("/{customerId}/deactivate")
    public ResponseEntity<CustomerResponse> deactivateCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.deactivateCustomerForAdmin(customerId));
    }
}
