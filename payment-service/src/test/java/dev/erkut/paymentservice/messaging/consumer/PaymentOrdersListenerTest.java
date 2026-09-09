package dev.erkut.paymentservice.messaging.consumer;

import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.message.command.Currency;
import dev.erkut.paymentservice.message.command.InitiatePaymentCommand;
import dev.erkut.paymentservice.payment.application.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentOrdersListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private PaymentService paymentService;

    private JsonMapper jsonMapper;
    private PaymentOrdersListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new PaymentOrdersListener(paymentService, new ConsumerUtil(jsonMapper));
    }

    @Test
    void handlePaymentCommands_shouldDeserializeInitiateCommandAndDelegate() {
        InitiatePaymentCommand expectedCommand = new InitiatePaymentCommand(
                ORDER_ID,
                new BigDecimal("123.45"),
                Currency.TRY
        );
        MessageEnvelope envelope = envelope(
                "INITIATE_PAYMENT_COMMAND",
                jsonMapper.valueToTree(expectedCommand)
        );
        ArgumentCaptor<InitiatePaymentCommand> commandCaptor =
                ArgumentCaptor.forClass(InitiatePaymentCommand.class);

        listener.handlePaymentCommands(envelope);

        verify(paymentService).handleInitiatePaymentCommand(eq(envelope), commandCaptor.capture());
        assertEquals(expectedCommand, commandCaptor.getValue());
    }

    @Test
    void handlePaymentCommands_shouldRejectUnsupportedMessageType() {
        MessageEnvelope envelope = envelope("UNKNOWN_PAYMENT_COMMAND", jsonMapper.createObjectNode());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> listener.handlePaymentCommands(envelope)
        );

        assertEquals("Unsupported order event type: UNKNOWN_PAYMENT_COMMAND", exception.getMessage());
        verifyNoInteractions(paymentService);
    }

    private static MessageEnvelope envelope(String messageType, tools.jackson.databind.JsonNode payload) {
        return new MessageEnvelope(MESSAGE_ID, messageType, OCCURRED_AT, payload);
    }
}
