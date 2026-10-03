package dev.erkut.paymentservice.provider.payment.stripe;

import com.stripe.net.Webhook;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.observability.metric.PaymentMetrics;
import dev.erkut.paymentservice.provider.payment.stripe.config.StripeProperties;
import dev.erkut.paymentservice.provider.payment.stripe.exception.StripeWebhookException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test";
    private static final String EVENT_TYPE = "checkout.session.completed";

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentMetrics paymentMetrics;

    private StripeWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new StripeWebhookService(
                new StripeProperties(
                        "test-secret",
                        WEBHOOK_SECRET,
                        "http://localhost/success",
                        "http://localhost/cancel",
                        30
                ),
                paymentService,
                paymentMetrics
        );
    }

    @Test
    void handle_shouldVerifyAndDelegatePaidCheckoutSession() throws Exception {
        String payload = payload(EVENT_TYPE, "paid");
        long eventCreatedAt = 1_700_000_000L;
        when(paymentService.handlePaymentCompleted(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(true);

        webhookService.handle(
                payload,
                Webhook.Signature.generateSignatureHeader(
                        payload,
                        WEBHOOK_SECRET,
                        Instant.now().getEpochSecond()
                )
        );

        verify(paymentService).handlePaymentCompleted(
                "evt_test_123",
                EVENT_TYPE,
                "cs_test_123",
                Instant.ofEpochSecond(eventCreatedAt)
        );
        verify(paymentMetrics).paymentCompleted();
    }

    @Test
    void handle_shouldIgnoreUnpaidCheckoutSession() throws Exception {
        String payload = payload(EVENT_TYPE, "unpaid");

        webhookService.handle(
                payload,
                Webhook.Signature.generateSignatureHeader(
                        payload,
                        WEBHOOK_SECRET,
                        Instant.now().getEpochSecond()
                )
        );

        verify(paymentService, never()).handlePaymentCompleted(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
    }

    @Test
    void handle_shouldDeserializeCheckoutSessionFromOlderStripeApiVersion() throws Exception {
        String eventType = "checkout.session.expired";
        String payload = payload(eventType, "unpaid", "2025-01-27.acacia");
        when(paymentService.handleCheckoutSessionExpired(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(true);

        webhookService.handle(
                payload,
                Webhook.Signature.generateSignatureHeader(
                        payload,
                        WEBHOOK_SECRET,
                        Instant.now().getEpochSecond()
                )
        );

        verify(paymentService).handleCheckoutSessionExpired(
                "evt_test_123",
                eventType,
                "cs_test_123",
                Instant.ofEpochSecond(1_700_000_000L)
        );
        verify(paymentMetrics).paymentExpired();
    }

    @Test
    void handle_shouldRejectInvalidSignature() {
        assertThrows(
                StripeWebhookException.class,
                () -> webhookService.handle(payload(EVENT_TYPE, "paid"), "invalid-signature")
        );
    }

    private static String payload(String eventType, String paymentStatus) {
        return payload(eventType, paymentStatus, "2026-08-26.dahlia");
    }

    private static String payload(String eventType, String paymentStatus, String apiVersion) {
        return """
                {
                  "id": "evt_test_123",
                  "object": "event",
                  "api_version": "%s",
                  "created": 1700000000,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "object": "checkout.session",
                      "payment_status": "%s"
                    }
                  }
                }
                """.formatted(apiVersion, eventType, paymentStatus);
    }
}
