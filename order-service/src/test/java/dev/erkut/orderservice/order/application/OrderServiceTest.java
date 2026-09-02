package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.order.domain.OrderStatus;
import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID SOURCE_CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createFromCheckout_validSnapshots_shouldCreatePendingStockOrderAndSave() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 2),
                new OrderLineSnapshot(PRODUCT_B, "Product B", new BigDecimal("50.00"), 3)
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        Order saved = orderService.createFromCheckout(
                SOURCE_CART_ID,
                CUSTOMER_ID,
                Currency.TRY,
                snapshots,
                CREATED_AT
        );

        verify(orderRepository).save(orderCaptor.capture());
        Order persisted = orderCaptor.getValue();
        assertSame(persisted, saved);
        assertEquals(OrderStatus.PENDING_STOCK, saved.getStatus());
        assertEquals(SOURCE_CART_ID, saved.getSourceCartId());
        assertEquals(CUSTOMER_ID, saved.getCustomerId());
        assertEquals(new BigDecimal("350.00"), saved.getTotalAmount());
        assertEquals(2, saved.getOrderItems().size());
        assertNull(saved.getRejectionReason());
        assertNull(saved.getConfirmedAt());
        assertNull(saved.getRejectedAt());
    }

    @Test
    void createFromCheckout_emptyItems_shouldThrowWithoutSaving() {
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createFromCheckout(
                        SOURCE_CART_ID,
                        CUSTOMER_ID,
                        Currency.TRY,
                        List.of(),
                        CREATED_AT
                )
        );

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void markStockReserved_shouldLoadDelegateAndLeaveStateMachineToEntity() {
        Order order = validOrder(CREATED_AT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = orderService.markStockReserved(ORDER_ID, UPDATED_AT);

        assertSame(order, result);
        assertEquals(OrderStatus.PENDING_PAYMENT, result.getStatus());
        assertEquals(UPDATED_AT, result.getUpdatedAt());
        verify(orderRepository).findById(ORDER_ID);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void markPaymentUnknown_shouldDelegateToEntity() {
        Order order = validOrder(CREATED_AT);
        order.markStockReserved(CREATED_AT.plusSeconds(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = orderService.markPaymentUnknown(ORDER_ID, UPDATED_AT);

        assertEquals(OrderStatus.PAYMENT_UNKNOWN, result.getStatus());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void markPaymentCompleted_shouldDelegateToEntity() {
        Order order = validOrder(CREATED_AT);
        order.markStockReserved(CREATED_AT.plusSeconds(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = orderService.markPaymentCompleted(ORDER_ID, UPDATED_AT);

        assertEquals(OrderStatus.PENDING_STOCK_CONFIRMATION, result.getStatus());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void confirm_shouldDelegateToEntity() {
        Order order = validOrder(CREATED_AT);
        order.markStockReserved(CREATED_AT.plusSeconds(1));
        order.markPaymentCompleted(CREATED_AT.plusSeconds(2));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = orderService.confirm(ORDER_ID, UPDATED_AT);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(UPDATED_AT, result.getConfirmedAt());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void reject_shouldDelegateToEntity() {
        Order order = validOrder(CREATED_AT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        Order result = orderService.reject(ORDER_ID, OrderRejectionReason.OUT_OF_STOCK, UPDATED_AT);

        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertEquals(OrderRejectionReason.OUT_OF_STOCK, result.getRejectionReason());
        assertEquals(UPDATED_AT, result.getRejectedAt());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void markStockReserved_whenEntityRejectsTransition_shouldPropagate() {
        Order order = validOrder(CREATED_AT);
        order.markStockReserved(CREATED_AT.plusSeconds(1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.markStockReserved(ORDER_ID, UPDATED_AT)
        );
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
    }

    @Test
    void markStockReserved_unknownOrder_shouldThrow() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.markStockReserved(ORDER_ID, UPDATED_AT)
        );
    }

    @Test
    void getOrders_withoutCustomerId_shouldReturnPaginatedOrders() {
        Order firstOrder = validOrder(CREATED_AT);
        Order secondOrder = validOrder(CREATED_AT);
        Pageable requestedPage = org.springframework.data.domain.PageRequest.of(1, 2);
        Page<Order> orders = new PageImpl<>(List.of(firstOrder, secondOrder), requestedPage, 5);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(orders);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        Page<OrderResponse> response = orderService.getOrders(null, 1, 2);

        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals(2, response.getContent().size());
        assertEquals(SOURCE_CART_ID, response.getContent().getFirst().sourceCartId());
        assertEquals(OrderStatus.PENDING_STOCK, response.getContent().getFirst().status());
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
        Order order = validOrder(CREATED_AT);
        Page<Order> orders = new PageImpl<>(List.of(order), org.springframework.data.domain.PageRequest.of(0, 2), 1);
        when(orderRepository.findAllByCustomerId(any(UUID.class), any(Pageable.class))).thenReturn(orders);
        ArgumentCaptor<UUID> customerCaptor = ArgumentCaptor.forClass(UUID.class);

        Page<OrderResponse> response = orderService.getOrders(CUSTOMER_ID, 0, 2);

        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        verify(orderRepository).findAllByCustomerId(customerCaptor.capture(), any(Pageable.class));
        assertEquals(CUSTOMER_ID, customerCaptor.getValue());
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getOrderById_shouldReturnMappedOrder() {
        Order order = validOrder(CREATED_AT);
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(ORDER_ID);

        assertEquals(SOURCE_CART_ID, response.sourceCartId());
        assertEquals(CUSTOMER_ID, response.customerId());
        assertEquals(OrderStatus.PENDING_STOCK, response.status());
        assertEquals(2, response.items().size());
        assertEquals(PRODUCT_A, response.items().getFirst().productId());
        assertEquals(new BigDecimal("350.00"), response.totalAmount());
        verify(orderRepository).findWithItemsById(ORDER_ID);
    }

    private static Order validOrder(Instant now) {
        return Order.create(
                SOURCE_CART_ID,
                CUSTOMER_ID,
                Currency.TRY,
                List.of(
                        new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 2),
                        new OrderLineSnapshot(PRODUCT_B, "Product B", new BigDecimal("50.00"), 3)
                ),
                now
        );
    }
}
