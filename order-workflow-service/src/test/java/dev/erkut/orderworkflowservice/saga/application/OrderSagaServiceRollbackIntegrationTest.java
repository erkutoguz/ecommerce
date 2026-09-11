package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.Currency;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaServiceRollbackIntegrationTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private OutboxMessageRepository outboxRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_messages");
        jdbcTemplate.update("DELETE FROM order_sagas");
        jdbcTemplate.update("DELETE FROM inbox_messages");
    }

    @Test
    void handleOrderCheckoutStarted_outboxSaveFailure_shouldRollbackInboxSagaAndOutbox() {
        OrderCheckoutStartedEvent event = new OrderCheckoutStartedEvent(
                ORDER_ID,
                CUSTOMER_ID,
                new BigDecimal("100.00"),
                Currency.TRY,
                List.of(new OrderCheckoutStartedEvent.OrderCheckoutItem(PRODUCT_ID, 1))
        );
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                OrderCheckoutStartedEvent.MESSAGE_TYPE,
                OCCURRED_AT,
                jsonMapper.valueToTree(event)
        );
        when(outboxRepository.save(any(OutboxMessage.class)))
                .thenThrow(new RuntimeException("outbox save failed"));

        assertThrows(
                RuntimeException.class,
                () -> orderSagaService.handleOrderCheckoutStarted(envelope, event)
        );

        assertEquals(0, rowCount("inbox_messages"));
        assertEquals(0, rowCount("order_sagas"));
        assertEquals(0, rowCount("outbox_messages"));
    }

    private long rowCount(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class
        );
        return count == null ? 0 : count;
    }
}
