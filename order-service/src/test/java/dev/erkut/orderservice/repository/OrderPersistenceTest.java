package dev.erkut.orderservice.repository;

import dev.erkut.orderservice.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:order-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderPersistenceTest {

    private static final UUID ORDER_ID = UUID.fromString("00000005-1111-4111-8111-000000000005");
    private static final UUID REMOVED_ITEM_ID = UUID.fromString("00000005-9999-4999-8999-000000000005");
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
    void removeItem_withoutSave_shouldDeleteOrphanAndUpdateTotal() {
        // Arrange
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // Act
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(ORDER_ID).orElseThrow();
            assertEquals(5, order.getItems().size());
            assertEquals(new BigDecimal("19375.00"), order.getTotalAmount());

            order.removeItem(REMOVED_ITEM_ID, UPDATED_AT);
            entityManager.flush();
        });

        // Assert
        Integer removedItemCount = jdbcTemplate.queryForObject(
                "select count(*) from order_items where order_id = ? and item_id = ?",
                Integer.class,
                ORDER_ID,
                REMOVED_ITEM_ID
        );
        BigDecimal persistedTotal = jdbcTemplate.queryForObject(
                "select total_amount from orders where id = ?",
                BigDecimal.class,
                ORDER_ID
        );

        assertEquals(0, removedItemCount);
        assertEquals(new BigDecimal("19150.00"), persistedTotal);
    }
}
