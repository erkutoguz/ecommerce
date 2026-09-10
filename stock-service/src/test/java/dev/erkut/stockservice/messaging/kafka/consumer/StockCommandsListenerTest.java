package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.reservation.application.ReservationService;
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

@ExtendWith(MockitoExtension.class)
class StockCommandsListenerTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private ReservationService reservationService;

    private JsonMapper jsonMapper;
    private StockCommandsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new StockCommandsListener(
                new ConsumerUtil(jsonMapper),
                reservationService
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

        listener.listenStockCommand(envelope);

        verify(reservationService).handleConfirmStockReservationCommand(
                eq(envelope),
                commandCaptor.capture()
        );
        assertEquals(ORDER_ID, commandCaptor.getValue().orderId());
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
