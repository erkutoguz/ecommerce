package dev.erkut.customerservice.customer.api.internal;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.customer.domain.exception.CustomerNotFoundException;
import dev.erkut.customerservice.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalCustomerController {

    private final CustomerService customerService;
    private final CurrentUser currentUser;

    public InternalCustomerController(CustomerService customerService, CurrentUser currentUser) {
        this.customerService = customerService;
        this.currentUser = currentUser;
    }

    @GetMapping("/by-auth-user/{authUserId}")
    public CustomerLookupResponse getByAuthUserId(
            @PathVariable UUID authUserId
    ) {
        if (!currentUser.authUserId().equals(authUserId)) {
            throw new CustomerNotFoundException("Customer not found");
        }
        return customerService.getByAuthUserId(authUserId);
    }
}
