package dev.erkut.paymentservice.provider.payment.stripe;

import com.stripe.net.Webhook;
import dev.erkut.paymentservice.payment.application.PaymentService;
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

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test";
    private static final String EVENT_TYPE = "checkout.session.completed";

    @Mock
    private PaymentService paymentService;

    private StripeWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new StripeWebhookService(
                new StripeProperties(
                        "test-secret",
                        WEBHOOK_SECRET,
                        "http://localhost/success",
                        "http://localhost/cancel"
                ),
                paymentService
        );
    }

    @Test
    void handle_shouldVerifyAndDelegatePaidCheckoutSession() throws Exception {
        String payload = payload(EVENT_TYPE, "paid");
        long eventCreatedAt = 1_700_000_000L;

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
    void handle_shouldRejectInvalidSignature() {
        assertThrows(
                StripeWebhookException.class,
                () -> webhookService.handle(payload(EVENT_TYPE, "paid"), "invalid-signature")
        );
    }

    private static String payload(String eventType, String paymentStatus) {
        return """
                {
                  "id": "evt_test_123",
                  "object": "event",
                  "api_version": "2026-08-26.dahlia",
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
                """.formatted(eventType, paymentStatus);
    }
}
