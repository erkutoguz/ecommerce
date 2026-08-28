package dev.erkut.customerservice;

import dev.erkut.customerservice.controller.CustomerController;
import dev.erkut.customerservice.dto.CustomerAddressCreateRequest;
import dev.erkut.customerservice.dto.CustomerAddressResponse;
import dev.erkut.customerservice.dto.CustomerCreateRequest;
import dev.erkut.customerservice.dto.CustomerResponse;
import dev.erkut.customerservice.exception.AddressNotFoundException;
import dev.erkut.customerservice.exception.CustomerEmailAlreadyExistsException;
import dev.erkut.customerservice.exception.CustomerNotFoundException;
import dev.erkut.customerservice.exception.GlobalExceptionHandler;
import dev.erkut.customerservice.exception.InvalidCustomerStateException;
import dev.erkut.customerservice.model.CustomerStatus;
import dev.erkut.customerservice.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADDRESS_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void createCustomer_validRequestReturnsCreated() throws Exception {
        when(customerService.createCustomer(any(CustomerCreateRequest.class)))
                .thenReturn(customerResponse(CustomerStatus.ACTIVE));

        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada Lovelace","email":"ada@example.com","phone":"+441234567890"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(customerService).createCustomer(
                new CustomerCreateRequest("Ada Lovelace", "ada@example.com", "+441234567890"));
    }

    @Test
    void createCustomer_invalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));

        verify(customerService, never()).createCustomer(any());
    }

    @Test
    void createCustomer_duplicateEmailReturnsConflict() throws Exception {
        when(customerService.createCustomer(any(CustomerCreateRequest.class)))
                .thenThrow(new CustomerEmailAlreadyExistsException("duplicate email"));

        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate email"));
    }

    @Test
    void getCustomerById_successReturnsOkAndMissingReturnsNotFound() throws Exception {
        when(customerService.getCustomerById(CUSTOMER_ID)).thenReturn(customerResponse(CustomerStatus.ACTIVE));
        when(customerService.getCustomerById(eq(ADDRESS_ID)))
                .thenThrow(new CustomerNotFoundException("customer missing"));

        mockMvc.perform(get("/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));
        mockMvc.perform(get("/customers/{customerId}", ADDRESS_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("customer missing"));
    }

    @Test
    void getCustomers_validPaginationReturnsOk() throws Exception {
        when(customerService.getCustomers(1, 2))
                .thenReturn(new PageImpl<>(List.of(customerResponse(CustomerStatus.ACTIVE))));

        mockMvc.perform(get("/customers").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(customerService).getCustomers(1, 2);
    }

    @Test
    void getCustomers_invalidPaginationReturnsBadRequest() throws Exception {
        for (String[] params : List.of(
                new String[]{"-1", "10"},
                new String[]{"0", "0"},
                new String[]{"0", "101"})) {
            mockMvc.perform(get("/customers").param("page", params[0]).param("size", params[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Request validation failed"));
        }

        verify(customerService, never()).getCustomers(any(Integer.class), any(Integer.class));
    }

    @Test
    void deactivateCustomer_successReturnsOk() throws Exception {
        when(customerService.deactivateCustomer(CUSTOMER_ID)).thenReturn(customerResponse(CustomerStatus.INACTIVE));

        mockMvc.perform(post("/customers/{customerId}/deactivate", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivateCustomer_missingOrInvalidStateReturnsExpectedErrors() throws Exception {
        when(customerService.deactivateCustomer(CUSTOMER_ID))
                .thenThrow(new CustomerNotFoundException("customer missing"));
        when(customerService.deactivateCustomer(ADDRESS_ID))
                .thenThrow(new InvalidCustomerStateException("inactive customer"));

        mockMvc.perform(post("/customers/{customerId}/deactivate", CUSTOMER_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/customers/{customerId}/deactivate", ADDRESS_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("inactive customer"));
    }

    @Test
    void addAddress_validRequestReturnsCreatedAddress() throws Exception {
        when(customerService.addCustomerAddress(eq(CUSTOMER_ID), any(CustomerAddressCreateRequest.class)))
                .thenReturn(new CustomerAddressResponse(ADDRESS_ID, "1 Main Street", "London", "United Kingdom"));

        mockMvc.perform(post("/customers/{customerId}/addresses", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullAddress":"1 Main Street","city":"London","country":"United Kingdom"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerAddressId").value(ADDRESS_ID.toString()))
                .andExpect(jsonPath("$.city").value("London"));
    }

    @Test
    void addAddress_invalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/customers/{customerId}/addresses", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullAddress\":\"\",\"city\":\"London\",\"country\":\"United Kingdom\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));

        verify(customerService, never()).addCustomerAddress(any(), any());
    }

    @Test
    void addAddress_missingCustomerOrInactiveCustomerReturnsExpectedErrors() throws Exception {
        when(customerService.addCustomerAddress(eq(CUSTOMER_ID), any(CustomerAddressCreateRequest.class)))
                .thenThrow(new CustomerNotFoundException("customer missing"));
        when(customerService.addCustomerAddress(eq(ADDRESS_ID), any(CustomerAddressCreateRequest.class)))
                .thenThrow(new InvalidCustomerStateException("inactive customer"));

        String body = "{\"fullAddress\":\"1 Main Street\",\"city\":\"London\",\"country\":\"United Kingdom\"}";
        mockMvc.perform(post("/customers/{customerId}/addresses", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/customers/{customerId}/addresses", ADDRESS_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void removeAddress_successReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/customers/{customerId}/addresses/{addressId}", CUSTOMER_ID, ADDRESS_ID))
                .andExpect(status().isNoContent());

        verify(customerService).removeCustomerAddress(CUSTOMER_ID, ADDRESS_ID);
    }

    @Test
    void removeAddress_missingCustomerOrAddressOrInactiveCustomerReturnsExpectedErrors() throws Exception {
        UUID missingCustomerId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID missingAddressId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        doThrow(new CustomerNotFoundException("customer missing"))
                .when(customerService).removeCustomerAddress(missingCustomerId, ADDRESS_ID);
        doThrow(new AddressNotFoundException("address missing"))
                .when(customerService).removeCustomerAddress(CUSTOMER_ID, missingAddressId);
        doThrow(new InvalidCustomerStateException("inactive customer"))
                .when(customerService).removeCustomerAddress(ADDRESS_ID, CUSTOMER_ID);

        mockMvc.perform(delete("/customers/{customerId}/addresses/{addressId}", missingCustomerId, ADDRESS_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/customers/{customerId}/addresses/{addressId}", CUSTOMER_ID, missingAddressId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/customers/{customerId}/addresses/{addressId}", ADDRESS_ID, CUSTOMER_ID))
                .andExpect(status().isConflict());
    }

    private static CustomerResponse customerResponse(CustomerStatus status) {
        return new CustomerResponse(
                CUSTOMER_ID, "Ada Lovelace", "ada@example.com", "+441234567890", status,
                List.of(), CREATED_AT, CREATED_AT);
    }
}
