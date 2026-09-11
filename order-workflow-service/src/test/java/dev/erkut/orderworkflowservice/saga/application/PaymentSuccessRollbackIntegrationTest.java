package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentCompletedEvent;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.domain.Currency;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentSuccessRollbackIntegrationTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000061");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000061");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa61");
    private static final Instant INITIAL_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private OrderSagaRepository orderSagaRepository;

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
    void handlePaymentCompleted_outboxPersistenceFailure_shouldRollbackInboxAndSaga() {
        OrderSaga saga = OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                INITIAL_AT
        );
        saga.markPaymentPending(INITIAL_AT.plusSeconds(1));
        orderSagaRepository.save(saga);

        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID);
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                "PAYMENT_COMPLETED_EVENT",
                INITIAL_AT.plusSeconds(2),
                jsonMapper.valueToTree(event)
        );
        when(outboxRepository.save(any(OutboxMessage.class)))
                .thenThrow(new RuntimeException("outbox save failed"));

        assertThrows(
                RuntimeException.class,
                () -> orderSagaService.handlePaymentCompletedEvent(envelope, event)
        );

        assertEquals(OrderSagaState.PAYMENT_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_messages",
                Integer.class
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_messages",
                Integer.class
        ));
    }
}
