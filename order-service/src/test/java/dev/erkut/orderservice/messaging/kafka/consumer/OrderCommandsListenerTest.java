package dev.erkut.orderservice.messaging.kafka.consumer;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.RejectOrderCommand;
import dev.erkut.orderservice.order.application.OrderService;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderCommandsListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000020");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000020");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OrderService orderService;

    private JsonMapper jsonMapper;
    private OrderCommandsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new OrderCommandsListener(new ConsumerUtil(jsonMapper), orderService);
    }

    @Test
    void rejectOrderCommand_shouldValidateDeserializeAndDelegate() {
        RejectOrderCommand expectedCommand = new RejectOrderCommand(
                ORDER_ID,
                OrderRejectionReason.OUT_OF_STOCK
        );
        MessageEnvelope envelope = envelope(
                "REJECT_ORDER_COMMAND",
                jsonMapper.valueToTree(expectedCommand)
        );
        ArgumentCaptor<RejectOrderCommand> commandCaptor =
                ArgumentCaptor.forClass(RejectOrderCommand.class);

        listener.listenOrderCommands(envelope);

        verify(orderService).handleRejectOrderCommand(eq(envelope), commandCaptor.capture());
        assertEquals(expectedCommand, commandCaptor.getValue());
    }

    @Test
    void unsupportedCommand_shouldThrowInsteadOfSilentlyIgnoring() {
        MessageEnvelope envelope = envelope("UNKNOWN_ORDER_COMMAND", jsonMapper.createObjectNode());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> listener.listenOrderCommands(envelope)
        );

        assertEquals("Unsupported order command type: UNKNOWN_ORDER_COMMAND", exception.getMessage());
        verifyNoInteractions(orderService);
    }

    private static MessageEnvelope envelope(String messageType, tools.jackson.databind.JsonNode payload) {
        return new MessageEnvelope(MESSAGE_ID, messageType, OCCURRED_AT, payload);
    }
}
