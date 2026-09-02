package dev.erkut.orderservice.order.domain;

import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {

    private static final UUID SOURCE_CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Test
    void create_validSnapshots_shouldCreatePendingStockOrder() {
        Order order = validOrder(CREATED_AT);

        assertEquals(OrderStatus.PENDING_STOCK, order.getStatus());
        assertNull(order.getRejectionReason());
        assertNull(order.getConfirmedAt());
        assertNull(order.getRejectedAt());
        assertEquals(SOURCE_CART_ID, order.getSourceCartId());
        assertEquals(CUSTOMER_ID, order.getCustomerId());
        assertEquals(Currency.TRY, order.getCurrency());
        assertEquals(CREATED_AT, order.getCreatedAt());
        assertEquals(CREATED_AT, order.getUpdatedAt());
        assertEquals(2, order.getOrderItems().size());
        assertEquals(PRODUCT_A, order.getOrderItems().getFirst().getProductId());
        assertEquals("Product A", order.getOrderItems().getFirst().getProductNameSnapshot());
        assertEquals(new BigDecimal("100.00"), order.getOrderItems().getFirst().getProductPriceSnapshot());
        assertEquals(2, order.getOrderItems().getFirst().getQuantity());
        assertEquals(PRODUCT_B, order.getOrderItems().get(1).getProductId());
        assertEquals("Product B", order.getOrderItems().get(1).getProductNameSnapshot());
        assertEquals(new BigDecimal("50.00"), order.getOrderItems().get(1).getProductPriceSnapshot());
        assertEquals(3, order.getOrderItems().get(1).getQuantity());
        assertEquals(new BigDecimal("350.00"), order.getTotalAmount());
    }

    @Test
    void create_emptyItemList_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, List.of(), CREATED_AT)
        );
    }

    @Test
    void create_nullItemList_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, null, CREATED_AT)
        );
    }

    @Test
    void create_duplicateProduct_shouldThrow() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 1),
                new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 2)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void create_nullProductId_shouldThrow() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(null, "Product A", new BigDecimal("100.00"), 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void create_blankProductName_shouldThrow() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(PRODUCT_A, "  ", new BigDecimal("100.00"), 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void create_nonPositivePrice_shouldThrow() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("0.00"), 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void create_nonPositiveQuantity_shouldThrow() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void create_nullSourceCartId_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(
                        null,
                        CUSTOMER_ID,
                        Currency.TRY,
                        List.of(new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 1)),
                        CREATED_AT
                )
        );
    }

    @Test
    void create_nullSnapshotElement_shouldThrow() {
        List<OrderLineSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 1));
        snapshots.add(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void create_priceWithMoreThanTwoDecimalPlaces_shouldThrow() {
        List<OrderLineSnapshot> snapshots = List.of(
                new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("10.999"), 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Order.create(SOURCE_CART_ID, CUSTOMER_ID, Currency.TRY, snapshots, CREATED_AT)
        );
    }

    @Test
    void getOrderItems_shouldBeUnmodifiable() {
        Order order = validOrder(CREATED_AT);

        assertThrows(UnsupportedOperationException.class, () -> order.getOrderItems().clear());
        assertEquals(2, order.getOrderItems().size());
    }

    @Test
    void markStockReserved_fromPendingStock_shouldMoveToPendingPayment() {
        Order order = validOrder(CREATED_AT);

        order.markStockReserved(UPDATED_AT);

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertEquals(CREATED_AT, order.getCreatedAt());
        assertNull(order.getConfirmedAt());
        assertNull(order.getRejectedAt());
        assertNull(order.getRejectionReason());
    }

    @Test
    void markPaymentUnknown_fromPendingPayment_shouldMoveToPaymentUnknown() {
        Order order = orderIn(OrderStatus.PENDING_PAYMENT);

        order.markPaymentUnknown(UPDATED_AT);

        assertEquals(OrderStatus.PAYMENT_UNKNOWN, order.getStatus());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertNull(order.getConfirmedAt());
        assertNull(order.getRejectedAt());
    }

    @Test
    void markPaymentCompleted_fromPendingPayment_shouldMoveToPendingStockConfirmation() {
        Order order = orderIn(OrderStatus.PENDING_PAYMENT);

        order.markPaymentCompleted(UPDATED_AT);

        assertEquals(OrderStatus.PENDING_STOCK_CONFIRMATION, order.getStatus());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertNull(order.getConfirmedAt());
        assertNull(order.getRejectedAt());
    }

    @Test
    void markPaymentCompleted_fromPaymentUnknown_shouldMoveToPendingStockConfirmation() {
        Order order = orderIn(OrderStatus.PAYMENT_UNKNOWN);

        order.markPaymentCompleted(UPDATED_AT);

        assertEquals(OrderStatus.PENDING_STOCK_CONFIRMATION, order.getStatus());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertNull(order.getConfirmedAt());
        assertNull(order.getRejectedAt());
    }

    @Test
    void confirm_fromPendingStockConfirmation_shouldConfirmAndSetConfirmedAt() {
        Order order = orderIn(OrderStatus.PENDING_STOCK_CONFIRMATION);

        order.confirm(UPDATED_AT);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(UPDATED_AT, order.getConfirmedAt());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertNull(order.getRejectedAt());
        assertNull(order.getRejectionReason());
        assertEquals(new BigDecimal("350.00"), order.getTotalAmount());
    }

    @Test
    void reject_fromPendingStock_shouldRejectAndSetReason() {
        Order order = validOrder(CREATED_AT);

        order.reject(OrderRejectionReason.OUT_OF_STOCK, UPDATED_AT);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(OrderRejectionReason.OUT_OF_STOCK, order.getRejectionReason());
        assertEquals(UPDATED_AT, order.getRejectedAt());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertNull(order.getConfirmedAt());
    }

    @Test
    void reject_fromPendingPayment_shouldRejectAndSetReason() {
        Order order = orderIn(OrderStatus.PENDING_PAYMENT);

        order.reject(OrderRejectionReason.PAYMENT_DECLINED, UPDATED_AT);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(OrderRejectionReason.PAYMENT_DECLINED, order.getRejectionReason());
        assertEquals(UPDATED_AT, order.getRejectedAt());
        assertNull(order.getConfirmedAt());
    }

    @Test
    void reject_fromPaymentUnknown_shouldRejectAndSetReason() {
        Order order = orderIn(OrderStatus.PAYMENT_UNKNOWN);

        order.reject(OrderRejectionReason.PAYMENT_DECLINED, UPDATED_AT);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(OrderRejectionReason.PAYMENT_DECLINED, order.getRejectionReason());
        assertEquals(UPDATED_AT, order.getRejectedAt());
        assertEquals(UPDATED_AT, order.getUpdatedAt());
        assertNull(order.getConfirmedAt());
    }

    @Test
    void reject_fromPaymentUnknown_withUserCancelled_shouldThrow() {
        Order order = orderIn(OrderStatus.PAYMENT_UNKNOWN);
        Instant before = order.getUpdatedAt();

        assertThrows(
                InvalidOrderStateException.class,
                () -> order.reject(OrderRejectionReason.USER_CANCELLED, UPDATED_AT)
        );
        assertEquals(OrderStatus.PAYMENT_UNKNOWN, order.getStatus());
        assertNull(order.getRejectionReason());
        assertNull(order.getRejectedAt());
        assertEquals(before, order.getUpdatedAt());
    }

    @Test
    void reject_fromPendingStock_withPaymentDeclined_shouldThrow() {
        Order order = validOrder(CREATED_AT);

        assertThrows(
                InvalidOrderStateException.class,
                () -> order.reject(OrderRejectionReason.PAYMENT_DECLINED, UPDATED_AT)
        );
        assertEquals(OrderStatus.PENDING_STOCK, order.getStatus());
        assertNull(order.getRejectionReason());
        assertNull(order.getRejectedAt());
        assertEquals(CREATED_AT, order.getUpdatedAt());
    }

    @Test
    void reject_nullReason_shouldThrow() {
        Order order = validOrder(CREATED_AT);

        assertThrows(IllegalArgumentException.class, () -> order.reject(null, UPDATED_AT));
        assertEquals(OrderStatus.PENDING_STOCK, order.getStatus());
        assertNull(order.getRejectedAt());
    }

    @Test
    void confirm_fromPendingStock_shouldThrow() {
        Order order = validOrder(CREATED_AT);

        assertThrows(InvalidOrderStateException.class, () -> order.confirm(UPDATED_AT));
        assertEquals(OrderStatus.PENDING_STOCK, order.getStatus());
        assertNull(order.getConfirmedAt());
        assertEquals(CREATED_AT, order.getUpdatedAt());
    }

    @Test
    void markStockReserved_fromPendingPayment_shouldThrow() {
        Order order = orderIn(OrderStatus.PENDING_PAYMENT);

        Instant before = order.getUpdatedAt();
        assertThrows(InvalidOrderStateException.class, () -> order.markStockReserved(UPDATED_AT));
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(before, order.getUpdatedAt());
    }

    @Test
    void reject_fromPendingStockConfirmation_shouldThrow() {
        Order order = orderIn(OrderStatus.PENDING_STOCK_CONFIRMATION);
        Instant before = order.getUpdatedAt();

        for (OrderRejectionReason reason : OrderRejectionReason.values()) {
            assertThrows(
                    InvalidOrderStateException.class,
                    () -> order.reject(reason, UPDATED_AT)
            );
        }

        assertEquals(OrderStatus.PENDING_STOCK_CONFIRMATION, order.getStatus());
        assertNull(order.getRejectionReason());
        assertNull(order.getRejectedAt());
        assertEquals(before, order.getUpdatedAt());
    }

    @Test
    void confirmedOrder_shouldRejectAllTransitions() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        Instant before = order.getUpdatedAt();
        Instant confirmedAt = order.getConfirmedAt();

        assertThrows(InvalidOrderStateException.class, () -> order.markStockReserved(UPDATED_AT));
        assertThrows(InvalidOrderStateException.class, () -> order.markPaymentUnknown(UPDATED_AT));
        assertThrows(InvalidOrderStateException.class, () -> order.markPaymentCompleted(UPDATED_AT));
        assertThrows(InvalidOrderStateException.class, () -> order.confirm(UPDATED_AT));
        assertThrows(
                InvalidOrderStateException.class,
                () -> order.reject(OrderRejectionReason.USER_CANCELLED, UPDATED_AT)
        );

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(before, order.getUpdatedAt());
        assertEquals(confirmedAt, order.getConfirmedAt());
        assertNull(order.getRejectedAt());
        assertNull(order.getRejectionReason());
    }

    @Test
    void rejectedOrder_shouldRejectAllTransitions() {
        Order order = orderIn(OrderStatus.REJECTED);
        Instant before = order.getUpdatedAt();
        Instant rejectedAt = order.getRejectedAt();
        OrderRejectionReason reason = order.getRejectionReason();

        assertThrows(InvalidOrderStateException.class, () -> order.markStockReserved(UPDATED_AT));
        assertThrows(InvalidOrderStateException.class, () -> order.markPaymentUnknown(UPDATED_AT));
        assertThrows(InvalidOrderStateException.class, () -> order.markPaymentCompleted(UPDATED_AT));
        assertThrows(InvalidOrderStateException.class, () -> order.confirm(UPDATED_AT));
        assertThrows(
                InvalidOrderStateException.class,
                () -> order.reject(OrderRejectionReason.PAYMENT_DECLINED, UPDATED_AT)
        );

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(before, order.getUpdatedAt());
        assertEquals(rejectedAt, order.getRejectedAt());
        assertEquals(reason, order.getRejectionReason());
        assertNull(order.getConfirmedAt());
    }

    @Test
    void orderItem_shouldExposeNoPublicMutationApi() {
        List<String> publicMutators = Arrays.stream(OrderItem.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> !name.startsWith("get"))
                .toList();

        assertTrue(publicMutators.isEmpty(), () -> "Unexpected public OrderItem API: " + publicMutators);
    }

    @Test
    void create_shouldNotExposePublicCreateOnOrderItem() {
        boolean hasPublicCreate = Arrays.stream(OrderItem.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("create") && Modifier.isPublic(method.getModifiers()));

        assertFalse(hasPublicCreate);
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

    private static Order orderIn(OrderStatus status) {
        Order order = validOrder(CREATED_AT);
        Instant t1 = CREATED_AT.plusSeconds(1);
        Instant t2 = CREATED_AT.plusSeconds(2);
        Instant t3 = CREATED_AT.plusSeconds(3);
        Instant t4 = CREATED_AT.plusSeconds(4);

        switch (status) {
            case PENDING_STOCK -> {
            }
            case PENDING_PAYMENT -> order.markStockReserved(t1);
            case PAYMENT_UNKNOWN -> {
                order.markStockReserved(t1);
                order.markPaymentUnknown(t2);
            }
            case PENDING_STOCK_CONFIRMATION -> {
                order.markStockReserved(t1);
                order.markPaymentCompleted(t2);
            }
            case CONFIRMED -> {
                order.markStockReserved(t1);
                order.markPaymentCompleted(t2);
                order.confirm(t3);
            }
            case REJECTED -> order.reject(OrderRejectionReason.OUT_OF_STOCK, t4);
        }
        return order;
    }
}
