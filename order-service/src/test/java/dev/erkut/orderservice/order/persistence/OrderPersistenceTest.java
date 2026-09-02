package dev.erkut.orderservice.order.persistence;

import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID SAVE_CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
    private static final UUID SAVE_CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID REJECT_CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
    private static final UUID REJECT_CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID PRODUCT_A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_shouldPersistSnapshotAndLifecycleFields() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        insertCart(SAVE_CART_ID, SAVE_CUSTOMER_ID);

        UUID orderId = transactionTemplate.execute(status -> {
            Order order = Order.create(
                    SAVE_CART_ID,
                    SAVE_CUSTOMER_ID,
                    Currency.TRY,
                    List.of(
                            new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 2),
                            new OrderLineSnapshot(PRODUCT_B, "Product B", new BigDecimal("50.00"), 3)
                    ),
                    CREATED_AT
            );
            return orderRepository.save(order).getId();
        });

        transactionTemplate.executeWithoutResult(status -> {
            Order reloaded = orderRepository.findWithItemsById(orderId).orElseThrow();
            assertEquals(SAVE_CART_ID, reloaded.getSourceCartId());
            assertEquals(SAVE_CUSTOMER_ID, reloaded.getCustomerId());
            assertEquals(OrderStatus.PENDING_STOCK, reloaded.getStatus());
            assertNull(reloaded.getRejectionReason());
            assertEquals(new BigDecimal("350.00"), reloaded.getTotalAmount());
            assertEquals(2, reloaded.getOrderItems().size());
            assertEquals(PRODUCT_A, reloaded.getOrderItems().getFirst().getProductId());
            assertEquals("Product A", reloaded.getOrderItems().getFirst().getProductNameSnapshot());
            assertEquals(new BigDecimal("100.00"), reloaded.getOrderItems().getFirst().getProductPriceSnapshot());
            assertEquals(2, reloaded.getOrderItems().getFirst().getQuantity());
            assertNull(reloaded.getConfirmedAt());
            assertNull(reloaded.getRejectedAt());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reject_shouldPersistRejectionReasonAndRejectedAt() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        insertCart(REJECT_CART_ID, REJECT_CUSTOMER_ID);

        UUID orderId = transactionTemplate.execute(status -> {
            Order order = Order.create(
                    REJECT_CART_ID,
                    REJECT_CUSTOMER_ID,
                    Currency.TRY,
                    List.of(new OrderLineSnapshot(PRODUCT_A, "Product A", new BigDecimal("100.00"), 1)),
                    CREATED_AT
            );
            return orderRepository.save(order).getId();
        });

        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.reject(OrderRejectionReason.OUT_OF_STOCK, UPDATED_AT);
            entityManager.flush();
        });

        String status = jdbcTemplate.queryForObject(
                "select status from orders where id = ?",
                String.class,
                orderId
        );
        String reason = jdbcTemplate.queryForObject(
                "select rejection_reason from orders where id = ?",
                String.class,
                orderId
        );

        assertEquals("REJECTED", status);
        assertEquals("OUT_OF_STOCK", reason);
    }

    private void insertCart(UUID cartId, UUID customerId) {
        jdbcTemplate.update(
                """
                        insert into carts (id, customer_id, status, created_at, updated_at)
                        values (?, ?, 'CHECKOUT_LOCKED', ?, ?)
                        """,
                cartId,
                customerId,
                java.sql.Timestamp.from(CREATED_AT),
                java.sql.Timestamp.from(CREATED_AT)
        );
    }
}
