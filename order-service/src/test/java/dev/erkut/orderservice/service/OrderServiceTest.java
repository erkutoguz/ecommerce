package dev.erkut.orderservice.service;

import dev.erkut.orderservice.dto.OrderCreateRequest;
import dev.erkut.orderservice.dto.OrderItemCreateRequest;
import dev.erkut.orderservice.dto.OrderResponse;
import dev.erkut.orderservice.dto.UpdateOrderItemRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

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

    @Test
    void updateOrderItem_validRequest_shouldReturnUpdatedOrder() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 2);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Act
        var response = orderService.updateOrderItem(
                ORDER_ID,
                KNOWN_PRODUCT_ID,
                new UpdateOrderItemRequest(3)
        );

        // Assert
        assertEquals(3, response.items().get(0).quantity());
        assertEquals(new BigDecimal("7500.00"), response.totalAmount());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void removeOrderItem_validRequest_shouldReturnUpdatedOrder() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        order.addItem(SECOND_PRODUCT_ID, "Wireless Mouse", new BigDecimal("900.00"), 2, UPDATED_AT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Act
        var response = orderService.removeOrderItem(ORDER_ID, KNOWN_PRODUCT_ID);

        // Assert
        assertEquals(1, response.items().size());
        assertEquals(SECOND_PRODUCT_ID, response.items().get(0).itemId());
        assertEquals(new BigDecimal("1800.00"), response.totalAmount());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void getOrders_withoutCustomerId_shouldReturnPaginatedOrders() {
        // Arrange
        Order firstOrder = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        Order secondOrder = orderWithItem(KNOWN_CUSTOMER_ID, SECOND_PRODUCT_ID, 2);
        Pageable requestedPage = org.springframework.data.domain.PageRequest.of(1, 2);
        Page<Order> orders = new PageImpl<>(List.of(firstOrder, secondOrder), requestedPage, 5);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(orders);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // Act
        Page<OrderResponse> response = orderService.getOrders(null, 1, 2);

        // Assert
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals(2, response.getContent().size());
        verify(orderRepository).findAll(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(2, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
        verify(orderRepository, never()).findAllByCustomerId(any(UUID.class), any(Pageable.class));
    }

    @Test
    void getOrders_withCustomerId_shouldReturnFilteredPaginatedOrders() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        Page<Order> orders = new PageImpl<>(List.of(order), org.springframework.data.domain.PageRequest.of(0, 2), 1);
        when(orderRepository.findAllByCustomerId(any(UUID.class), any(Pageable.class))).thenReturn(orders);
        ArgumentCaptor<UUID> customerCaptor = ArgumentCaptor.forClass(UUID.class);

        // Act
        Page<OrderResponse> response =
                orderService.getOrders(KNOWN_CUSTOMER_ID, 0, 2);

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        verify(orderRepository).findAllByCustomerId(customerCaptor.capture(), any(Pageable.class));
        assertEquals(KNOWN_CUSTOMER_ID, customerCaptor.getValue());
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    private Order orderWithItem(UUID customerId, UUID itemId, int quantity) {
        Order order = Order.create(customerId, Currency.TRY, CREATED_AT);
        order.addItem(itemId, "Test Product", new BigDecimal("2500.00"), quantity, UPDATED_AT);
        return order;
    }
}
