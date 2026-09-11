package dev.erkut.paymentservice;

import dev.erkut.paymentservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.message.command.Currency;
import dev.erkut.paymentservice.message.command.InitiatePaymentCommand;
import dev.erkut.paymentservice.message.event.PaymentCompletedEvent;
import dev.erkut.paymentservice.message.event.PaymentFailureReason;
import dev.erkut.paymentservice.outbox.application.OutboxService;
import dev.erkut.paymentservice.outbox.domain.OutboxMessageType;
import dev.erkut.paymentservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException;
import dev.erkut.paymentservice.payment.domain.Payment;
import dev.erkut.paymentservice.payment.domain.PaymentStatus;
import dev.erkut.paymentservice.payment.persistence.PaymentRepository;
import dev.erkut.paymentservice.provider.payment.PaymentProvider;
import dev.erkut.paymentservice.provider.payment.PaymentSession;
import dev.erkut.paymentservice.provider.payment.exception.PaymentProviderException;
import dev.erkut.paymentservice.provider.payment.stripe.StripeWebhookService;
import com.stripe.net.Webhook;
import dev.erkut.paymentservice.provider.payment.stripe.inbox.persistence.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "payment.stripe.secret-key=test-secret",
        "payment.stripe.webhook-secret=test-whsec",
        "outbox.relay.enabled=false"
})
@AutoConfigureMockMvc
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
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StripeWebhookService stripeWebhookService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @MockitoSpyBean
    private OutboxService outboxService;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        inboxRepository.deleteAll();
        webhookEventRepository.deleteAll();
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
    void payments_shouldRejectNullProviderDetailsAtDatabaseBoundary() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertPaymentWithNullProviderDetails(
                        UUID.fromString("80000000-0000-0000-0000-000000000105"),
                        null,
                        "https://checkout.stripe.com/test"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertPaymentWithNullProviderDetails(
                        UUID.fromString("80000000-0000-0000-0000-000000000106"),
                        "cs_test_123",
                        null
                )
        );
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

    @Test
    void handlePaymentCompleted_shouldCompletePaymentAndCreateOutbox() {
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000201");
        persistAwaitingPayment(orderId, "cs_test_completed");

        paymentService.handlePaymentCompleted(
                "evt_test_completed",
                "checkout.session.completed",
                "cs_test_completed",
                OCCURRED_AT
        );

        Payment payment = paymentRepository.findById(orderId).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(OCCURRED_AT, payment.getProcessedAt());
        assertEquals(1, webhookEventRepository.count());
        assertEquals(1, outboxRepository.count());

        var outboxMessage = outboxRepository.findAll().getFirst();
        assertEquals(orderId, outboxMessage.getAggregateId());
        assertEquals("PAYMENT_COMPLETED_EVENT", outboxMessage.getMessageType().name());
        assertEquals(orderId.toString(), outboxMessage.getPayload().get("orderId").asText());
    }

    @Test
    void handlePaymentCompleted_shouldTreatSameWebhookEventAsNoOp() {
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000202");
        persistAwaitingPayment(orderId, "cs_test_duplicate");

        paymentService.handlePaymentCompleted(
                "evt_test_duplicate",
                "checkout.session.completed",
                "cs_test_duplicate",
                OCCURRED_AT
        );
        paymentService.handlePaymentCompleted(
                "evt_test_duplicate",
                "checkout.session.completed",
                "cs_test_duplicate",
                OCCURRED_AT.plusSeconds(1)
        );

        assertEquals(1, webhookEventRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals(PaymentStatus.COMPLETED,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void handlePaymentCompleted_shouldRollbackWebhookInboxWhenPaymentIsMissing() {
        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.handlePaymentCompleted(
                        "evt_test_missing_payment",
                        "checkout.session.completed",
                        "cs_test_missing_payment",
                        OCCURRED_AT
                )
        );

        assertEquals(0, webhookEventRepository.count());
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void handlePaymentCompleted_shouldRollbackPaymentAndInboxWhenOutboxFails() {
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000203");
        persistAwaitingPayment(orderId, "cs_test_outbox_failure");
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService)
                .handlePaymentCompletedEvent(any(PaymentCompletedEvent.class), any(Instant.class));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.handlePaymentCompleted(
                        "evt_test_outbox_failure",
                        "checkout.session.completed",
                        "cs_test_outbox_failure",
                        OCCURRED_AT
                )
        );

        assertEquals(0, webhookEventRepository.count());
        assertEquals(0, outboxRepository.count());
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void checkoutSessionExpired_shouldFailPaymentAndCreateCanonicalFailureEvent() {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_expired";
        long eventCreated = 1_700_000_000L;
        persistAwaitingPayment(orderId, providerPaymentId);
        String payload = expiredPayload("evt_test_expired", providerPaymentId, eventCreated);

        stripeWebhookService.handle(payload, signature(payload));

        Payment payment = paymentRepository.findById(orderId).orElseThrow();
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(Instant.ofEpochSecond(eventCreated), payment.getProcessedAt());
        assertEquals(1, webhookEventRepository.count());
        assertEquals(1, outboxRepository.count());

        var outboxMessage = outboxRepository.findAll().getFirst();
        assertEquals(OutboxMessageType.PAYMENT_FAILED_EVENT, outboxMessage.getMessageType());
        assertEquals(orderId, outboxMessage.getAggregateId());
        assertEquals(2, outboxMessage.getPayload().size());
        assertEquals(orderId.toString(), outboxMessage.getPayload().get("orderId").asText());
        assertEquals(PaymentFailureReason.SESSION_EXPIRED.name(),
                outboxMessage.getPayload().get("failureReason").asText());
    }

    @Test
    void webhookController_shouldProcessSignedCompletedEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_controller_completed";
        persistAwaitingPayment(orderId, providerPaymentId);
        String payload = completedPayload("evt_test_controller_completed", providerPaymentId, 1_700_000_010L);

        mockMvc.perform(post("/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertEquals(PaymentStatus.COMPLETED,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, webhookEventRepository.count());
        assertEquals(OutboxMessageType.PAYMENT_COMPLETED_EVENT,
                outboxRepository.findAll().getFirst().getMessageType());
    }

    @Test
    void webhookController_shouldProcessSignedExpiredEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_controller_expired";
        persistAwaitingPayment(orderId, providerPaymentId);
        String payload = expiredPayload("evt_test_controller_expired", providerPaymentId, 1_700_000_011L);

        mockMvc.perform(post("/payments/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertEquals(PaymentStatus.FAILED,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, webhookEventRepository.count());
        assertEquals(OutboxMessageType.PAYMENT_FAILED_EVENT,
                outboxRepository.findAll().getFirst().getMessageType());
    }

    @Test
    void checkoutSessionExpired_duplicateWebhookId_shouldBeIdempotent() {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_expired_duplicate";
        persistAwaitingPayment(orderId, providerPaymentId);
        String payload = expiredPayload("evt_test_expired_duplicate", providerPaymentId, 1_700_000_001L);

        stripeWebhookService.handle(payload, signature(payload));
        stripeWebhookService.handle(payload, signature(payload));

        assertEquals(PaymentStatus.FAILED,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, webhookEventRepository.count());
        assertEquals(1, outboxRepository.count());
    }

    @Test
    void checkoutSessionExpired_afterCompletedPayment_shouldBeAcceptedAsStaleNoOp() {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_expired_after_completed";
        persistAwaitingPayment(orderId, providerPaymentId);
        paymentService.handlePaymentCompleted(
                "evt_test_completed_before_expiry",
                "checkout.session.completed",
                providerPaymentId,
                OCCURRED_AT
        );

        String payload = expiredPayload(
                "evt_test_expired_after_completed",
                providerPaymentId,
                1_700_000_002L
        );

        assertDoesNotThrow(() -> stripeWebhookService.handle(payload, signature(payload)));

        assertEquals(PaymentStatus.COMPLETED,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, webhookEventRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals(0, outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType() == OutboxMessageType.PAYMENT_FAILED_EVENT)
                .count());
    }

    @Test
    void checkoutSessionExpired_afterFailedPayment_shouldBeAcceptedAsStaleNoOp() {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_expired_after_failed";
        persistAwaitingPayment(orderId, providerPaymentId);
        String firstPayload = expiredPayload("evt_test_expired_first", providerPaymentId, 1_700_000_003L);
        String secondPayload = expiredPayload("evt_test_expired_second", providerPaymentId, 1_700_000_004L);

        stripeWebhookService.handle(firstPayload, signature(firstPayload));
        stripeWebhookService.handle(secondPayload, signature(secondPayload));

        assertEquals(PaymentStatus.FAILED,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, webhookEventRepository.count());
        assertEquals(1, outboxRepository.count());
    }

    @Test
    void checkoutSessionExpired_outboxFailure_shouldRollbackPaymentAndWebhookInbox() {
        UUID orderId = UUID.randomUUID();
        String providerPaymentId = "cs_test_expired_outbox_failure";
        persistAwaitingPayment(orderId, providerPaymentId);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService)
                .handlePaymentFailedEvent(any(), any(Instant.class));

        assertThrows(IllegalStateException.class, () -> paymentService.handleCheckoutSessionExpired(
                "evt_test_expired_outbox_failure",
                "checkout.session.expired",
                providerPaymentId,
                OCCURRED_AT
        ));

        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION,
                paymentRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, webhookEventRepository.count());
        assertEquals(0, outboxRepository.count());
    }

    private void persistAwaitingPayment(UUID orderId, String providerPaymentId) {
        Payment payment = Payment.create(
                orderId,
                new BigDecimal("100.00"),
                dev.erkut.paymentservice.payment.domain.Currency.TRY,
                OCCURRED_AT.minusSeconds(30)
        );
        payment.markAwaitingCustomerAction(
                providerPaymentId,
                "https://checkout.stripe.com/test",
                OCCURRED_AT.minusSeconds(20)
        );
        paymentRepository.save(payment);
    }

    private void insertPaymentWithNullProviderDetails(
            UUID orderId,
            String providerPaymentId,
            String checkoutUrl
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payments (
                    order_id, version, total_amount, currency, status,
                    provider_payment_id, checkout_url, updated_at, created_at, processed_at
                ) VALUES (?, 0, ?, 'TRY', 'AWAITING_CUSTOMER_ACTION', ?, ?, ?, ?, NULL)
                """,
                orderId,
                new BigDecimal("100.00"),
                providerPaymentId,
                checkoutUrl,
                Timestamp.from(OCCURRED_AT),
                Timestamp.from(OCCURRED_AT)
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

    private String expiredPayload(String eventId, String providerPaymentId, long eventCreated) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-08-26.dahlia",
                  "created": %d,
                  "type": "checkout.session.expired",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session"
                    }
                  }
                }
                """.formatted(eventId, eventCreated, providerPaymentId);
    }

    private String completedPayload(String eventId, String providerPaymentId, long eventCreated) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2026-08-26.dahlia",
                  "created": %d,
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "payment_status": "paid"
                    }
                  }
                }
                """.formatted(eventId, eventCreated, providerPaymentId);
    }

    private String signature(String payload) {
        try {
            return Webhook.Signature.generateSignatureHeader(
                    payload,
                    "test-whsec",
                    Instant.now().getEpochSecond()
            );
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate Stripe webhook signature", exception);
        }
    }

}
