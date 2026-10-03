package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.observability.metric.StockMetrics;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.application.ReservationProcessingOutcome;
import dev.erkut.stockservice.reservation.application.ReservationService;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.annotation.KafkaListener;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandsListenerTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private OutboxMessageRepository outboxRepository;

    private JsonMapper jsonMapper;
    private SimpleMeterRegistry meterRegistry;
    private StockCommandsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        meterRegistry = new SimpleMeterRegistry();
        listener = new StockCommandsListener(
                new ConsumerUtil(jsonMapper),
                reservationService,
                new StockMetrics(meterRegistry, reservationRepository, outboxRepository)
        );
    }

    @Test
    void confirmationCommandIsDeserializedAndDelegated() {
        MessageEnvelope envelope = envelope(
                "CONFIRM_STOCK_RESERVATION_COMMAND",
                jsonMapper.valueToTree(new ConfirmStockReservationCommand(ORDER_ID))
        );
        ArgumentCaptor<ConfirmStockReservationCommand> commandCaptor =
                ArgumentCaptor.forClass(ConfirmStockReservationCommand.class);
        when(reservationService.handleConfirmStockReservationCommand(eq(envelope), any()))
                .thenReturn(ReservationProcessingOutcome.CONFIRMED);

        listener.listenStockCommand(envelope);

        verify(reservationService).handleConfirmStockReservationCommand(
                eq(envelope),
                commandCaptor.capture()
        );
        assertEquals(ORDER_ID, commandCaptor.getValue().orderId());
        assertEquals(1.0, meterRegistry.counter("stock.reservations.confirmed").count());
    }

    @Test
    void reservationCommandRecordsBusinessOutcomeAfterServiceReturns() {
        MessageEnvelope envelope = envelope(
                "RESERVE_STOCK_COMMAND",
                jsonMapper.valueToTree(new ReserveStockCommand(ORDER_ID, java.util.List.of(
                        new ReserveStockCommand.ReserveStockItem(UUID.randomUUID(), 1)
                )))
        );
        when(reservationService.handleReserveStock(eq(envelope), any()))
                .thenReturn(ReservationProcessingOutcome.FAILED_INSUFFICIENT_STOCK);

        listener.listenStockCommand(envelope);

        assertEquals(1.0, meterRegistry.counter(
                "stock.reservations.failed", "reason", "insufficient_stock"
        ).count());
    }

    @Test
    void duplicateReservationCommandDoesNotRecordBusinessMetrics() {
        MessageEnvelope envelope = envelope(
                "RESERVE_STOCK_COMMAND",
                jsonMapper.valueToTree(new ReserveStockCommand(ORDER_ID, java.util.List.of(
                        new ReserveStockCommand.ReserveStockItem(UUID.randomUUID(), 1)
                )))
        );
        when(reservationService.handleReserveStock(eq(envelope), any()))
                .thenReturn(ReservationProcessingOutcome.DUPLICATE);

        listener.listenStockCommand(envelope);

        assertEquals(0.0, meterRegistry.counter("stock.reservations.reserved").count());
        assertEquals(0.0, meterRegistry.counter(
                "stock.reservations.failed", "reason", "insufficient_stock"
        ).count());
    }

    @Test
    void listenerUsesStockCommandsTopic() throws NoSuchMethodException {
        KafkaListener annotation = StockCommandsListener.class
                .getMethod("listenStockCommand", MessageEnvelope.class)
                .getAnnotation(KafkaListener.class);

        assertEquals("${kafka.topic.stock-commands}", annotation.topics()[0]);
    }

    @Test
    void businessExceptionIsNotSwallowed() {
        RuntimeException failure = new RuntimeException("confirmation failed");
        doThrow(failure).when(reservationService)
                .handleConfirmStockReservationCommand(any(), any());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> listener.listenStockCommand(envelope(
                        "CONFIRM_STOCK_RESERVATION_COMMAND",
                        jsonMapper.valueToTree(new ConfirmStockReservationCommand(ORDER_ID))
                )));

        assertSame(failure, thrown);
    }

    private static MessageEnvelope envelope(String messageType,
                                            tools.jackson.databind.JsonNode payload) {
        return new MessageEnvelope(MESSAGE_ID, messageType, OCCURRED_AT, payload);
    }
}
