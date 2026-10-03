package dev.erkut.orderservice.messaging.kafka.consumer;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.ConfirmOrderCommand;
import dev.erkut.orderservice.message.command.MarkOrderPaymentCompletedCommand;
import dev.erkut.orderservice.message.command.MarkOrderStockReservedCommand;
import dev.erkut.orderservice.message.command.RejectOrderCommand;
import dev.erkut.orderservice.order.application.OrderService;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.observability.metric.OrderMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandsListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000020");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000020");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OrderService orderService;

    private JsonMapper jsonMapper;
    private SimpleMeterRegistry meterRegistry;
    private OrderCommandsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        meterRegistry = new SimpleMeterRegistry();
        listener = new OrderCommandsListener(
                new ConsumerUtil(jsonMapper),
                orderService,
                new OrderMetrics(meterRegistry)
        );
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
        when(orderService.handleRejectOrderCommand(eq(envelope), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        ArgumentCaptor<RejectOrderCommand> commandCaptor =
                ArgumentCaptor.forClass(RejectOrderCommand.class);

        listener.listenOrderCommands(envelope);

        verify(orderService).handleRejectOrderCommand(eq(envelope), commandCaptor.capture());
        assertEquals(expectedCommand, commandCaptor.getValue());
        assertEquals(1.0, meterRegistry.counter(
                "orders.rejected", "reason", "out_of_stock"
        ).count());
    }

    @Test
    void duplicateRejectOrderCommand_shouldNotIncrementRejectionMetric() {
        RejectOrderCommand command = new RejectOrderCommand(ORDER_ID, OrderRejectionReason.OUT_OF_STOCK);
        MessageEnvelope envelope = envelope("REJECT_ORDER_COMMAND", jsonMapper.valueToTree(command));
        when(orderService.handleRejectOrderCommand(eq(envelope), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        listener.listenOrderCommands(envelope);

        assertEquals(0.0, meterRegistry.counter(
                "orders.rejected", "reason", "out_of_stock"
        ).count());
    }

    @Test
    void markOrderStockReservedCommand_shouldDeserializeAndDelegate() {
        MarkOrderStockReservedCommand expectedCommand = new MarkOrderStockReservedCommand(ORDER_ID);
        MessageEnvelope envelope = envelope(
                "MARK_ORDER_STOCK_RESERVED_COMMAND",
                jsonMapper.valueToTree(expectedCommand)
        );
        ArgumentCaptor<MarkOrderStockReservedCommand> commandCaptor =
                ArgumentCaptor.forClass(MarkOrderStockReservedCommand.class);

        listener.listenOrderCommands(envelope);

        verify(orderService).handleMarkOrderStockReservedCommand(eq(envelope), commandCaptor.capture());
        assertEquals(expectedCommand, commandCaptor.getValue());
    }

    @Test
    void markOrderPaymentCompletedCommand_shouldDeserializeAndDelegate() {
        MarkOrderPaymentCompletedCommand expectedCommand = new MarkOrderPaymentCompletedCommand(ORDER_ID);
        MessageEnvelope envelope = envelope(
                "MARK_ORDER_PAYMENT_COMPLETED_COMMAND",
                jsonMapper.valueToTree(expectedCommand)
        );
        ArgumentCaptor<MarkOrderPaymentCompletedCommand> commandCaptor =
                ArgumentCaptor.forClass(MarkOrderPaymentCompletedCommand.class);

        listener.listenOrderCommands(envelope);

        verify(orderService).handleMarkOrderPaymentCompletedCommand(eq(envelope), commandCaptor.capture());
        assertEquals(expectedCommand, commandCaptor.getValue());
    }

    @Test
    void confirmOrderCommand_shouldDeserializeAndDelegate() {
        ConfirmOrderCommand expectedCommand = new ConfirmOrderCommand(ORDER_ID);
        MessageEnvelope envelope = envelope(
                "CONFIRM_ORDER_COMMAND",
                jsonMapper.valueToTree(expectedCommand)
        );
        ArgumentCaptor<ConfirmOrderCommand> commandCaptor =
                ArgumentCaptor.forClass(ConfirmOrderCommand.class);

        listener.listenOrderCommands(envelope);

        verify(orderService).handleConfirmOrderCommand(eq(envelope), commandCaptor.capture());
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
