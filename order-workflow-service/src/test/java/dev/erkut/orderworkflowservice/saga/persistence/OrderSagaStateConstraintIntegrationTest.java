package dev.erkut.orderworkflowservice.saga.persistence;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrderSagaStateConstraintIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000050");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa50");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T10:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sagaStateConstraint_shouldAllowOrderConfirmationPending() {
        jdbcTemplate.update("""
                INSERT INTO order_sagas (
                    order_id,
                    customer_id,
                    version,
                    currency,
                    total_amount,
                    state,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 0, 'TRY', 100.00, ?, ?, ?)
                """,
                ORDER_ID,
                CUSTOMER_ID,
                OrderSagaState.ORDER_CONFIRMATION_PENDING.name(),
                NOW,
                NOW
        );

        String persistedState = jdbcTemplate.queryForObject(
                "SELECT state FROM order_sagas WHERE order_id = ?",
                String.class,
                ORDER_ID
        );

        assertEquals(OrderSagaState.ORDER_CONFIRMATION_PENDING.name(), persistedState);
    }
}
