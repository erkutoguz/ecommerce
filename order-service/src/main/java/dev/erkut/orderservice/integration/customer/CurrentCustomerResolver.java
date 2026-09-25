package dev.erkut.orderservice.integration.customer;

import dev.erkut.orderservice.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentCustomerResolver {

    private final CurrentUser currentUser;
    private final CustomerClient customerClient;

    public CurrentCustomerResolver(
            CurrentUser currentUser,
            CustomerClient customerClient
    ) {
        this.currentUser = currentUser;
        this.customerClient = customerClient;
    }

    public CustomerLookupResponse resolve() {
        UUID authUserId = currentUser.authUserId();

        CustomerLookupResponse customer =
                customerClient.getByAuthUserId(authUserId);

        if (customer.status() != CustomerStatus.ACTIVE) {
            throw new InvalidCustomerStateException("Customer is not active");
        }

        return customer;
    }

    public UUID customerId() {
        return resolve().customerId();
    }
}