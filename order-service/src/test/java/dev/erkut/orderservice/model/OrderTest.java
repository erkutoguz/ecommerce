package dev.erkut.orderservice.model;

import dev.erkut.orderservice.exception.InvalidOrderStateException;
import dev.erkut.orderservice.exception.OrderItemNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ITEM_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ITEM_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID MISSING_ITEM_ID = UUID.fromString("90000000-0000-0000-0000-000000000099");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Test
    void create_shouldCreatePendingOrder() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);

        // Act

        // Assert
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void addItem_shouldAddItemAndCalculateTotal() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);

        // Act
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 2, UPDATED_AT);

        // Assert
        assertEquals(1, order.getItems().size());
        assertEquals(new BigDecimal("5000.00"), order.getTotalAmount());
    }

    @Test
    void addSameItem_shouldIncreaseQuantityInsteadOfCreatingDuplicate() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 2, UPDATED_AT);

        // Act
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 3, UPDATED_AT);

        // Assert
        assertEquals(1, order.getItems().size());
        assertEquals(5, order.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("12500.00"), order.getTotalAmount());
    }

    @Test
    void addItem_whenOrderConfirmed_shouldThrowInvalidOrderStateException() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 1, UPDATED_AT);
        order.confirm(UPDATED_AT);

        // Act
        // Assert
        assertThrows(
                InvalidOrderStateException.class,
                () -> order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 1, UPDATED_AT)
        );
    }

    @Test
    void confirm_shouldChangeStatusToConfirmed() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 1, UPDATED_AT);

        // Act
        order.confirm(UPDATED_AT);

        // Assert
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void changeItemQuantity_shouldUpdateQuantityAndTotal() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 2, UPDATED_AT);

        // Act
        order.changeItemQuantity(ITEM_ID, 3, UPDATED_AT);

        // Assert
        assertEquals(3, order.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("7500.00"), order.getTotalAmount());
    }

    @Test
    void changeItemQuantity_whenOrderIsNotPending_shouldThrowInvalidOrderStateException() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 1, UPDATED_AT);
        order.confirm(UPDATED_AT);

        // Act
        // Assert
        assertThrows(
                InvalidOrderStateException.class,
                () -> order.changeItemQuantity(ITEM_ID, 2, UPDATED_AT)
        );
    }

    @Test
    void removeItem_shouldRemoveItemAndRecalculateTotal() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 1, UPDATED_AT);
        order.addItem(SECOND_ITEM_ID, "Wireless Mouse", new BigDecimal("900.00"), 2, UPDATED_AT);

        // Act
        order.removeItem(ITEM_ID, UPDATED_AT);

        // Assert
        assertEquals(1, order.getItems().size());
        assertEquals(SECOND_ITEM_ID, order.getItems().get(0).getItemId());
        assertEquals(new BigDecimal("1800.00"), order.getTotalAmount());
    }

    @Test
    void removeLastItem_shouldThrow() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);
        order.addItem(ITEM_ID, "Mechanical Keyboard", new BigDecimal("2500.00"), 1, UPDATED_AT);

        // Act
        // Assert
        assertThrows(
                IllegalStateException.class,
                () -> order.removeItem(ITEM_ID, UPDATED_AT)
        );
    }

    @Test
    void reject_shouldChangeStatusToRejected() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);

        // Act
        order.reject(UPDATED_AT);

        // Assert
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    @Test
    void cancel_shouldChangeStatusToCancelled() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);

        // Act
        order.cancel(UPDATED_AT);

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void removeMissingItem_shouldThrowOrderItemNotFoundException() {
        // Arrange
        Order order = Order.create(CUSTOMER_ID, Currency.TRY, CREATED_AT);

        // Act
        // Assert
        assertThrows(
                OrderItemNotFoundException.class,
                () -> order.removeItem(MISSING_ITEM_ID, UPDATED_AT)
        );
    }
}
