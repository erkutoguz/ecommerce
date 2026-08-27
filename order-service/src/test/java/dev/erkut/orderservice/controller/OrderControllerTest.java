package dev.erkut.orderservice.controller;

import dev.erkut.orderservice.dto.OrderResponse;
import dev.erkut.orderservice.exception.CustomerNotFoundException;
import dev.erkut.orderservice.exception.GlobalExceptionHandler;
import dev.erkut.orderservice.exception.ProductNotFoundException;
import dev.erkut.orderservice.model.Currency;
import dev.erkut.orderservice.model.OrderStatus;
import dev.erkut.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrder_validRequest_shouldReturnSuccessStatus() throws Exception {
        // Arrange
        when(orderService.createOrder(any())).thenReturn(new OrderResponse(
                null,
                CUSTOMER_ID,
                List.of(),
                OrderStatus.PENDING,
                Currency.TRY,
                new BigDecimal("0.00"),
                CREATED_AT,
                CREATED_AT
        ));
        String requestBody = """
                {
                  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "currency": "TRY",
                  "items": [
                    {
                      "itemId": "90000000-0000-0000-0000-000000000001",
                      "quantity": 2
                    }
                  ]
                }
                """;

        // Act
        // Assert
        mockMvc.perform(post("/orders")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrder_emptyItems_shouldReturnBadRequest() throws Exception {
        // Arrange
        String requestBody = """
                {
                  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "currency": "TRY",
                  "items": []
                }
                """;

        // Act
        // Assert
        mockMvc.perform(post("/orders")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_customerNotFound_shouldReturnNotFound() throws Exception {
        // Arrange
        when(orderService.createOrder(any()))
                .thenThrow(new CustomerNotFoundException("Customer not found"));
        String requestBody = """
                {
                  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "currency": "TRY",
                  "items": [
                    {
                      "itemId": "90000000-0000-0000-0000-000000000001",
                      "quantity": 1
                    }
                  ]
                }
                """;

        // Act
        // Assert
        mockMvc.perform(post("/orders")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_productNotFound_shouldReturnNotFound() throws Exception {
        // Arrange
        when(orderService.createOrder(any()))
                .thenThrow(new ProductNotFoundException("Product not found"));
        String requestBody = """
                {
                  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "currency": "TRY",
                  "items": [
                    {
                      "itemId": "90000000-0000-0000-0000-000000000099",
                      "quantity": 1
                    }
                  ]
                }
                """;

        // Act
        // Assert
        mockMvc.perform(post("/orders")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }
}
