package dev.erkut.orderservice.integration.customer;

import dev.erkut.orderservice.security.CurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentCustomerResolverTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private CurrentUser currentUser;

    @Mock
    private CustomerClient customerClient;

    @InjectMocks
    private CurrentCustomerResolver resolver;

    @Test
    void customerId_shouldResolveCustomerIdFromDistinctAuthUserId() {
        when(currentUser.authUserId()).thenReturn(AUTH_USER_ID);
        when(currentUser.accessToken()).thenReturn("access-token");
        when(customerClient.getByAuthUserId(AUTH_USER_ID, "access-token"))
                .thenReturn(new CustomerLookupResponse(CUSTOMER_ID, CustomerStatus.ACTIVE));

        assertEquals(CUSTOMER_ID, resolver.customerId());
    }

    @Test
    void customerId_inactiveCustomer_shouldThrowInvalidCustomerStateException() {
        when(currentUser.authUserId()).thenReturn(AUTH_USER_ID);
        when(currentUser.accessToken()).thenReturn("access-token");
        when(customerClient.getByAuthUserId(AUTH_USER_ID, "access-token"))
                .thenReturn(new CustomerLookupResponse(CUSTOMER_ID, CustomerStatus.INACTIVE));

        assertThrows(InvalidCustomerStateException.class, resolver::customerId);
    }
}
