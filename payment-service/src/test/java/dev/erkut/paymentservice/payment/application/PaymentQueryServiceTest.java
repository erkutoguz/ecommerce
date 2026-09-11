package dev.erkut.paymentservice.payment.application;

import dev.erkut.paymentservice.inbox.application.InboxService;
import dev.erkut.paymentservice.outbox.application.OutboxService;
import dev.erkut.paymentservice.payment.api.response.PaymentResponse;
import dev.erkut.paymentservice.payment.domain.Currency;
import dev.erkut.paymentservice.payment.domain.Payment;
import dev.erkut.paymentservice.payment.domain.PaymentStatus;
import dev.erkut.paymentservice.payment.persistence.PaymentRepository;
import dev.erkut.paymentservice.provider.payment.PaymentProvider;
import dev.erkut.paymentservice.provider.payment.stripe.inbox.application.WebhookEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final String CHECKOUT_URL = "https://checkout.stripe.com/c/test-session";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InboxService inboxService;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void getPaymentByOrderId_returnsPersistedCheckpointWithoutChangingPaymentOrCallingProviders() {
        Payment payment = payment();
        when(paymentRepository.findById(ORDER_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrderId(ORDER_ID);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION, response.status());
        assertEquals(CHECKOUT_URL, response.checkoutUrl());
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION, payment.getStatus());
        assertEquals(CHECKOUT_URL, payment.getCheckoutUrl());
        verify(paymentRepository).findById(ORDER_ID);
        verifyNoInteractions(inboxService, paymentProvider, webhookEventService, outboxService);
    }

    @Test
    void getPaymentByOrderId_whenMissingThrowsNotFound() {
        when(paymentRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(
                dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException.class,
                () -> paymentService.getPaymentByOrderId(ORDER_ID)
        );
        verify(paymentRepository).findById(ORDER_ID);
        verifyNoInteractions(inboxService, paymentProvider, webhookEventService, outboxService);
    }

    @Test
    void getPaymentByOrderId_preservesPersistedCheckoutUrlAfterCompletion() {
        Payment payment = payment();
        payment.markCompleted(CREATED_AT.plusSeconds(2), CREATED_AT.plusSeconds(3));
        when(paymentRepository.findById(ORDER_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrderId(ORDER_ID);

        assertEquals(PaymentStatus.COMPLETED, response.status());
        assertEquals(CHECKOUT_URL, response.checkoutUrl());
    }

    private Payment payment() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("100.00"), Currency.TRY, CREATED_AT);
        payment.markAwaitingCustomerAction("cs_test_session", CHECKOUT_URL, CREATED_AT.plusSeconds(1));
        return payment;
    }
}
