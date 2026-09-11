package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailedEvent;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailureReason;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationReleasedEvent;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.domain.Currency;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaFailureReason;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaState;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaCompensationRollbackIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000071");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa71");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private OrderSagaRepository orderSagaRepository;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxMessageRepository outboxRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_messages");
        jdbcTemplate.update("DELETE FROM order_sagas");
        jdbcTemplate.update("DELETE FROM inbox_messages");
    }

    @Test
    void paymentFailureOutboxFailure_shouldRollbackSagaAndInbox() {
        orderSagaRepository.save(paymentPendingSaga());
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("outbox unavailable"));
        PaymentFailedEvent event = new PaymentFailedEvent(
                ORDER_ID,
                PaymentFailureReason.SESSION_EXPIRED
        );
        UUID messageId = UUID.randomUUID();

        assertThrows(RuntimeException.class,
                () -> orderSagaService.handlePaymentFailedEvent(
                        envelope(messageId, "PAYMENT_FAILED_EVENT", event), event));

        assertEquals(OrderSagaState.PAYMENT_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, rowCount("outbox_messages"));
    }

    @Test
    void stockReleasedOutboxFailure_shouldRollbackSagaAndInbox() {
        OrderSaga saga = paymentPendingSaga();
        saga.markStockReservationReleasePending(
                CREATED_AT.plusSeconds(1),
                OrderSagaFailureReason.PAYMENT_EXPIRED
        );
        orderSagaRepository.save(saga);
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("outbox unavailable"));
        StockReservationReleasedEvent event = new StockReservationReleasedEvent(ORDER_ID);
        UUID messageId = UUID.randomUUID();

        assertThrows(RuntimeException.class,
                () -> orderSagaService.handleStockReservationReleasedEvent(
                        envelope(messageId, "STOCK_RESERVATION_RELEASED_EVENT", event), event));

        assertEquals(OrderSagaState.STOCK_RELEASE_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, rowCount("outbox_messages"));
    }

    private OrderSaga paymentPendingSaga() {
        OrderSaga saga = OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                CREATED_AT
        );
        saga.markPaymentPending(CREATED_AT.plusSeconds(1));
        return saga;
    }

    private MessageEnvelope envelope(UUID messageId, String messageType, Object event) {
        return new MessageEnvelope(
                messageId,
                messageType,
                CREATED_AT.plusSeconds(2),
                new JsonMapper().valueToTree(event)
        );
    }

    private long rowCount(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class
        );
        return count == null ? 0 : count;
    }
}
