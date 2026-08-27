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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
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

    @Test
    void getOrders_validPageAndSize_shouldReturnSuccessStatus() throws Exception {
        // Arrange
        when(orderService.getOrders(null, 1, 5)).thenReturn(pageOfOrders());

        // Act
        // Assert
        mockMvc.perform(get("/orders")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(orderService).getOrders(null, 1, 5);
    }

    @Test
    void getOrders_withCustomerId_shouldReturnSuccessStatus() throws Exception {
        // Arrange
        when(orderService.getOrders(CUSTOMER_ID, 0, 10)).thenReturn(pageOfOrders());

        // Act
        // Assert
        mockMvc.perform(get("/orders")
                        .param("customerId", CUSTOMER_ID.toString()))
                .andExpect(status().isOk());
        verify(orderService).getOrders(CUSTOMER_ID, 0, 10);
    }

    @Test
    void getOrders_invalidPageOrSize_shouldReturnBadRequest() throws Exception {
        // Arrange

        // Act
        // Assert
        mockMvc.perform(get("/orders").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeOrderItem_validRequest_shouldReturnSuccessStatus() throws Exception {
        // Arrange
        when(orderService.removeOrderItem(ORDER_ID, ITEM_ID)).thenReturn(orderResponse());

        // Act
        // Assert
        mockMvc.perform(delete("/orders/{orderId}/items/{itemId}", ORDER_ID, ITEM_ID))
                .andExpect(status().isOk());
        verify(orderService).removeOrderItem(ORDER_ID, ITEM_ID);
    }

    @Test
    void updateOrderItem_validRequest_shouldReturnSuccessStatus() throws Exception {
        // Arrange
        when(orderService.updateOrderItem(any(UUID.class), any(UUID.class), any()))
                .thenReturn(orderResponse());

        // Act
        // Assert
        mockMvc.perform(patch("/orders/{orderId}/items/{itemId}", ORDER_ID, ITEM_ID)
                        .contentType("application/json")
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk());
        verify(orderService).updateOrderItem(any(UUID.class), any(UUID.class), any());
    }

    private Page<OrderResponse> pageOfOrders() {
        return new PageImpl<>(List.of(orderResponse()), PageRequest.of(0, 10), 1);
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(
                ORDER_ID,
                CUSTOMER_ID,
                List.of(),
                OrderStatus.PENDING,
                Currency.TRY,
                new BigDecimal("0.00"),
                CREATED_AT,
                CREATED_AT
        );
    }
}
