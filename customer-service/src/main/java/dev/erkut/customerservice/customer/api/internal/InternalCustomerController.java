package dev.erkut.customerservice.customer.api.internal;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/customer")
    public CustomerLookupResponse getCurrentCustomer() {
        UUID authUserId = currentUser.authUserId();
        return customerService.getByAuthUserId(authUserId);
    }
}
