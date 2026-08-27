package dev.erkut.orderservice.service;

import dev.erkut.orderservice.dto.OrderCreateRequest;
import dev.erkut.orderservice.dto.OrderItemCreateRequest;
import dev.erkut.orderservice.exception.CustomerNotFoundException;
import dev.erkut.orderservice.exception.ProductNotFoundException;
import dev.erkut.orderservice.model.Currency;
import dev.erkut.orderservice.model.Order;
import dev.erkut.orderservice.model.OrderStatus;
import dev.erkut.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID KNOWN_CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID UNKNOWN_CUSTOMER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID KNOWN_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID UNKNOWN_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000099");

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_validRequest_shouldSaveOrder() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemCreateRequest(KNOWN_PRODUCT_ID, 2))
        );
        Order savedOrder = Order.create(KNOWN_CUSTOMER_ID, Currency.TRY, java.time.Instant.parse("2026-01-01T10:00:00Z"));
        savedOrder.addItem(
                KNOWN_PRODUCT_ID,
                "Mechanical Keyboard",
                new java.math.BigDecimal("2500.00"),
                2,
                java.time.Instant.parse("2026-01-01T10:00:00Z")
        );
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        orderService.createOrder(request);

        // Assert
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_unknownCustomer_shouldThrowCustomerNotFoundException() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(
                UNKNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemCreateRequest(KNOWN_PRODUCT_ID, 1))
        );

        // Act
        // Assert
        assertThrows(CustomerNotFoundException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_unknownProduct_shouldThrowProductNotFoundException() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemCreateRequest(UNKNOWN_PRODUCT_ID, 1))
        );

        // Act
        // Assert
        assertThrows(ProductNotFoundException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any(Order.class));
    }
}
