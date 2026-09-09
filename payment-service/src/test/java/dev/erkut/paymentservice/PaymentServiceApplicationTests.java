package dev.erkut.paymentservice;

import dev.erkut.paymentservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.message.command.Currency;
import dev.erkut.paymentservice.message.command.InitiatePaymentCommand;
import dev.erkut.paymentservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.payment.domain.Payment;
import dev.erkut.paymentservice.payment.domain.PaymentStatus;
import dev.erkut.paymentservice.payment.persistence.PaymentRepository;
import dev.erkut.paymentservice.provider.payment.PaymentProvider;
import dev.erkut.paymentservice.provider.payment.PaymentSession;
import dev.erkut.paymentservice.provider.payment.exception.PaymentProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "payment.stripe.secret-key=test-secret")
@Testcontainers
class PaymentServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void handleInitiatePaymentCommand_shouldPersistAwaitingPaymentAndInboxWithoutOutboxEvent() {
        UUID messageId = UUID.fromString("70000000-0000-0000-0000-000000000101");
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000101");
        InitiatePaymentCommand command = new InitiatePaymentCommand(
                orderId,
                new BigDecimal("100.00"),
                Currency.TRY
        );
        MessageEnvelope envelope = envelope(messageId, command);
        when(paymentProvider.initializePaymentSession(
                orderId,
                new BigDecimal("100.00"),
                dev.erkut.paymentservice.payment.domain.Currency.TRY
        )).thenReturn(new PaymentSession("cs_test_123", "https://checkout.stripe.com/test"));

        paymentService.handleInitiatePaymentCommand(envelope, command);

        Payment payment = paymentRepository.findById(orderId).orElseThrow();
        assertEquals(1, inboxRepository.count());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, new BigDecimal("100.00").compareTo(payment.getTotalAmount()));
        assertEquals(dev.erkut.paymentservice.payment.domain.Currency.TRY, payment.getCurrency());
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION, payment.getStatus());
        assertEquals("cs_test_123", payment.getProviderPaymentId());
        assertEquals("https://checkout.stripe.com/test", payment.getCheckoutUrl());
        assertNull(payment.getProcessedAt());
        assertEquals(0, outboxRepository.count());
        verify(paymentProvider).initializePaymentSession(
                orderId,
                new BigDecimal("100.00"),
                dev.erkut.paymentservice.payment.domain.Currency.TRY
        );
    }

    @Test
    void handleInitiatePaymentCommand_shouldTreatSameMessageIdAsNoOp() {
        UUID messageId = UUID.fromString("70000000-0000-0000-0000-000000000102");
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000102");
        InitiatePaymentCommand command = new InitiatePaymentCommand(
                orderId,
                new BigDecimal("100.00"),
                Currency.TRY
        );
        MessageEnvelope envelope = envelope(messageId, command);
        when(paymentProvider.initializePaymentSession(
                orderId,
                new BigDecimal("100.00"),
                dev.erkut.paymentservice.payment.domain.Currency.TRY
        )).thenReturn(new PaymentSession("cs_test_123", "https://checkout.stripe.com/test"));

        paymentService.handleInitiatePaymentCommand(envelope, command);
        paymentService.handleInitiatePaymentCommand(envelope, command);

        assertEquals(1, inboxRepository.count());
        assertEquals(1, paymentRepository.count());
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, outboxRepository.count());
        verify(paymentProvider).initializePaymentSession(
                orderId,
                new BigDecimal("100.00"),
                dev.erkut.paymentservice.payment.domain.Currency.TRY
        );
    }

    @Test
    void handleInitiatePaymentCommand_providerFailure_shouldRollbackInboxAndPayment() {
        UUID messageId = UUID.fromString("70000000-0000-0000-0000-000000000103");
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000103");
        InitiatePaymentCommand command = new InitiatePaymentCommand(
                orderId,
                new BigDecimal("100.00"),
                Currency.TRY
        );
        when(paymentProvider.initializePaymentSession(
                eq(orderId),
                eq(new BigDecimal("100.00")),
                eq(dev.erkut.paymentservice.payment.domain.Currency.TRY)
        )).thenThrow(new PaymentProviderException("Stripe unavailable", new RuntimeException()));

        assertThrows(
                PaymentProviderException.class,
                () -> paymentService.handleInitiatePaymentCommand(envelope(messageId, command), command)
        );

        assertEquals(0, inboxRepository.count());
        assertTrue(paymentRepository.findById(orderId).isEmpty());
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void handleInitiatePaymentCommand_shouldRejectInvalidInputWithoutClaimingInbox() {
        InitiatePaymentCommand validCommand = new InitiatePaymentCommand(
                UUID.fromString("80000000-0000-0000-0000-000000000104"),
                new BigDecimal("100.00"),
                Currency.TRY
        );
        InitiatePaymentCommand invalidAmountCommand = new InitiatePaymentCommand(
                UUID.fromString("80000000-0000-0000-0000-000000000105"),
                BigDecimal.ZERO,
                Currency.TRY
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.handleInitiatePaymentCommand(null, validCommand)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.handleInitiatePaymentCommand(envelope(UUID.randomUUID(), validCommand), null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.handleInitiatePaymentCommand(
                        envelope(UUID.fromString("70000000-0000-0000-0000-000000000105"), invalidAmountCommand),
                        invalidAmountCommand
                )
        );

        assertEquals(0, inboxRepository.count());
        assertEquals(0, paymentRepository.count());
        assertEquals(0, outboxRepository.count());
        verify(paymentProvider, never()).initializePaymentSession(
                eq(invalidAmountCommand.orderId()),
                eq(invalidAmountCommand.totalAmount()),
                eq(dev.erkut.paymentservice.payment.domain.Currency.TRY)
        );
    }

    private MessageEnvelope envelope(UUID messageId, InitiatePaymentCommand command) {
        return new MessageEnvelope(
                messageId,
                "INITIATE_PAYMENT_COMMAND",
                OCCURRED_AT,
                jsonMapper.valueToTree(command)
        );
    }

}
