package dev.erkut.customerservice.customer.api.internal;

import dev.erkut.customerservice.customer.application.CustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalCustomerController {

    private final CustomerService customerService;

    public InternalCustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/by-auth-user/{authUserId}")
    public CustomerLookupResponse getByAuthUserId(
            @PathVariable UUID authUserId
    ) {
        return customerService.getByAuthUserId(authUserId);
    }
}
