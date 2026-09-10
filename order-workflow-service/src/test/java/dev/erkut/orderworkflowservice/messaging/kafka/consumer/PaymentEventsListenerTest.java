package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.PaymentCompletedEvent;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventsListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000060");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000060");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OrderSagaService sagaService;

    private JsonMapper jsonMapper;
    private PaymentEventsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new PaymentEventsListener(
                sagaService,
                new ConsumerUtil(jsonMapper)
        );
    }

    @Test
    void handlePaymentEvents_paymentCompleted_shouldDeserializeAndDelegate() {
        PaymentCompletedEvent expectedEvent = new PaymentCompletedEvent(ORDER_ID);
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                "PAYMENT_COMPLETED_EVENT",
                OCCURRED_AT,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<PaymentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentCompletedEvent.class);

        listener.handlePaymentEvents(envelope);

        verify(sagaService).handlePaymentCompletedEvent(eq(envelope), eventCaptor.capture());
        assertEquals(expectedEvent, eventCaptor.getValue());
    }
}
