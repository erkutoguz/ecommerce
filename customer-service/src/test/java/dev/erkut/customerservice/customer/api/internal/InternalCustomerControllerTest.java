package dev.erkut.customerservice.customer.api.internal;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.customer.api.error.GlobalExceptionHandler;
import dev.erkut.customerservice.customer.domain.CustomerStatus;
import dev.erkut.customerservice.customer.domain.exception.CustomerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalCustomerController.class)
@Import(GlobalExceptionHandler.class)
class InternalCustomerControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void knownAuthUserIdReturnsMinimalCustomerLookup() throws Exception {
        when(customerService.getByAuthUserId(AUTH_USER_ID))
                .thenReturn(new CustomerLookupResponse(CUSTOMER_ID, CustomerStatus.ACTIVE));

        mockMvc.perform(get("/internal/by-auth-user/{authUserId}", AUTH_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());

        verify(customerService).getByAuthUserId(AUTH_USER_ID);
    }

    @Test
    void unknownAuthUserIdReturnsNotFound() throws Exception {
        when(customerService.getByAuthUserId(AUTH_USER_ID))
                .thenThrow(new CustomerNotFoundException("Customer not found with auth id: " + AUTH_USER_ID));

        mockMvc.perform(get("/internal/by-auth-user/{authUserId}", AUTH_USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Customer not found with auth id: " + AUTH_USER_ID));
    }
}
