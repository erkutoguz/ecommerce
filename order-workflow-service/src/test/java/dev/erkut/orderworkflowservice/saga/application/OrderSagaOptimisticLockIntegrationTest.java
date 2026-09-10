package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailedEvent;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailureReason;
import dev.erkut.orderworkflowservice.message.event.StockReservedEvent;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.domain.Currency;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaState;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaOptimisticLockIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000030");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa30");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000030");
    private static final UUID SUCCESS_MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000030");
    private static final UUID FAILURE_MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000031");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private OrderSagaService orderSagaService;

    @MockitoSpyBean
    private OrderSagaRepository orderSagaRepository;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_messages");
        jdbcTemplate.update("DELETE FROM order_sagas");
        jdbcTemplate.update("DELETE FROM inbox_messages");
    }

    @Test
    void concurrentStockSuccessAndFailure_shouldAllowExactlyOneSagaTransition() throws Exception {
        orderSagaRepository.save(OrderSaga.start(
                ORDER_ID,
                new BigDecimal("1234.56"),
                Currency.EUR,
                CUSTOMER_ID,
                CREATED_AT
        ));

        CountDownLatch bothTransactionsReadSameVersion = new CountDownLatch(2);
        doAnswer(invocation -> {
            Optional<OrderSaga> result = Optional.ofNullable(
                    entityManager.find(OrderSaga.class, ORDER_ID)
            );
            bothTransactionsReadSameVersion.countDown();
            if (!bothTransactionsReadSameVersion.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Both transactions did not read the same Saga version");
            }
            return result;
        }).when(orderSagaRepository).findById(ORDER_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> success = executor.submit(this::handleSuccess);
            Future<Attempt> failure = executor.submit(this::handleFailure);

            Outcome successOutcome = outcome(success, SUCCESS_MESSAGE_ID);
            Outcome failureOutcome = outcome(failure, FAILURE_MESSAGE_ID);
            List<Outcome> outcomes = List.of(successOutcome, failureOutcome);

            assertEquals(1, outcomes.stream().filter(Outcome::succeeded).count());
            assertEquals(1, outcomes.stream().filter(outcome -> !outcome.succeeded()).count());
            Outcome winner = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow();
            Outcome loser = outcomes.stream().filter(outcome -> !outcome.succeeded()).findFirst().orElseThrow();
            assertTrue(isOptimisticLockFailure(loser.failure()));

            OrderSaga finalSaga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
            OutboxMessageType expectedCommand = finalSaga.getState() == OrderSagaState.PAYMENT_PENDING
                    ? OutboxMessageType.INITIATE_PAYMENT_COMMAND
                    : OutboxMessageType.REJECT_ORDER_COMMAND;
            assertTrue(finalSaga.getState() == OrderSagaState.PAYMENT_PENDING
                    || finalSaga.getState() == OrderSagaState.ORDER_REJECTION_PENDING);
            assertEquals(1, outboxRepository.count());
            assertEquals(expectedCommand, outboxRepository.findAll().getFirst().getMessageType());
            assertTrue(inboxRepository.existsById(winner.messageId()));
            assertFalse(inboxRepository.existsById(loser.messageId()));
            assertEquals(1, inboxRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt handleSuccess() {
        StockReservedEvent event = new StockReservedEvent(ORDER_ID, CREATED_AT.plusSeconds(1));
        orderSagaService.handleStockReservedEvent(
                envelope(SUCCESS_MESSAGE_ID, "STOCK_RESERVED_EVENT", event),
                event
        );
        return new Attempt(SUCCESS_MESSAGE_ID);
    }

    private Attempt handleFailure() {
        StockReservationFailedEvent event = new StockReservationFailedEvent(
                ORDER_ID,
                StockReservationFailureReason.INSUFFICIENT_STOCK,
                PRODUCT_ID
        );
        orderSagaService.handleStockReservationFailedEvent(
                envelope(FAILURE_MESSAGE_ID, "STOCK_RESERVATION_FAILED_EVENT", event),
                event
        );
        return new Attempt(FAILURE_MESSAGE_ID);
    }

    private MessageEnvelope envelope(UUID messageId, String messageType, Object event) {
        return new MessageEnvelope(
                messageId,
                messageType,
                CREATED_AT.plusSeconds(1),
                jsonMapper.valueToTree(event)
        );
    }

    private static Outcome outcome(Future<Attempt> future, UUID expectedMessageId) throws Exception {
        try {
            return new Outcome(future.get(15, TimeUnit.SECONDS).messageId(), null);
        } catch (ExecutionException exception) {
            return new Outcome(expectedMessageId, exception.getCause());
        }
    }

    private static boolean isOptimisticLockFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.dao.OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Attempt(UUID messageId) {}

    private record Outcome(UUID messageId, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}
