package dev.erkut.orderservice.order.api;

import dev.erkut.orderservice.order.api.error.GlobalExceptionHandler;
import dev.erkut.orderservice.order.api.response.OrderItemResponse;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.application.OrderService;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.OrderStatus;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SOURCE_CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void getOrders_validPageAndSize_shouldReturnSuccessStatus() throws Exception {
        when(orderService.getOrders(null, 1, 5)).thenReturn(pageOfOrders());

        mockMvc.perform(get("/orders")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sourceCartId").value(SOURCE_CART_ID.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_STOCK"));
        verify(orderService).getOrders(null, 1, 5);
    }

    @Test
    void getOrders_withCustomerId_shouldReturnSuccessStatus() throws Exception {
        when(orderService.getOrders(CUSTOMER_ID, 0, 10)).thenReturn(pageOfOrders());

        mockMvc.perform(get("/orders")
                        .param("customerId", CUSTOMER_ID.toString()))
                .andExpect(status().isOk());
        verify(orderService).getOrders(CUSTOMER_ID, 0, 10);
    }

    @Test
    void getOrders_invalidPageOrSize_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/orders").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderById_existingOrder_shouldReturnSuccessStatus() throws Exception {
        when(orderService.getOrderById(ORDER_ID)).thenReturn(orderResponse());

        mockMvc.perform(get("/orders/{orderId}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.sourceCartId").value(SOURCE_CART_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_STOCK"))
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.totalAmount").value(200.00));
        verify(orderService).getOrderById(ORDER_ID);
    }

    @Test
    void getOrderById_unknownOrder_shouldReturnNotFound() throws Exception {
        when(orderService.getOrderById(ORDER_ID))
                .thenThrow(new OrderNotFoundException("Order not found with id: " + ORDER_ID));

        mockMvc.perform(get("/orders/{orderId}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found with id: " + ORDER_ID));
    }

    private Page<OrderResponse> pageOfOrders() {
        return new PageImpl<>(List.of(orderResponse()), PageRequest.of(0, 10), 1);
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(
                ORDER_ID,
                SOURCE_CART_ID,
                CUSTOMER_ID,
                List.of(new OrderItemResponse(
                        PRODUCT_ID,
                        "Product A",
                        new BigDecimal("100.00"),
                        2
                )),
                OrderStatus.PENDING_STOCK,
                null,
                Currency.TRY,
                new BigDecimal("200.00"),
                CREATED_AT,
                CREATED_AT,
                null,
                null
        );
    }
}
