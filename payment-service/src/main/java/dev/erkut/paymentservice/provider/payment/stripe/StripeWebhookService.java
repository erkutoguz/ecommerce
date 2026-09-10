package dev.erkut.paymentservice.provider.payment.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.provider.payment.stripe.config.StripeProperties;
import dev.erkut.paymentservice.provider.payment.stripe.exception.StripeWebhookException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class StripeWebhookService {

    private static final String CHECKOUT_SESSION_COMPLETED =
            "checkout.session.completed";

    private final StripeProperties properties;
    private final PaymentService paymentService;

    public StripeWebhookService(
            StripeProperties properties,
            PaymentService paymentService
    ) {
        this.properties = properties;
        this.paymentService = paymentService;
    }

    public void handle(String payload, String signature) {
        Event event = verifyAndConstructEvent(payload, signature);

        switch (event.getType()) {
            case CHECKOUT_SESSION_COMPLETED ->
                    handleCheckoutSessionCompleted(event);
        }
    }

    private Event verifyAndConstructEvent(
            String payload,
            String signature
    ) {
        if (payload == null || payload.isBlank()) {
            throw new StripeWebhookException("Stripe webhook payload cannot be blank");
        }

        if (signature == null || signature.isBlank()) {
            throw new StripeWebhookException("Stripe signature cannot be blank");
        }

        try {
            return Webhook.constructEvent(
                    payload,
                    signature,
                    properties.webhookSecret()
            );
        } catch (SignatureVerificationException e) {
            throw new StripeWebhookException(
                    "Stripe webhook signature verification failed",
                    e
            );
        } catch (RuntimeException e) {
            throw new StripeWebhookException(
                    "Stripe webhook payload could not be parsed",
                    e
            );
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        StripeObject stripeObject = event
                .getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() ->
                        new StripeWebhookException("Stripe checkout session could not be deserialized")
                );

        if (!(stripeObject instanceof Session session)) {
            throw new StripeWebhookException("Stripe event does not contain a checkout session");
        }

        if (!"paid".equals(session.getPaymentStatus())) {
            return;
        }

        String providerPaymentId = session.getId();

        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new StripeWebhookException("Stripe checkout session id cannot be blank");
        }

        paymentService.handlePaymentCompleted(
                event.getId(),
                event.getType(),
                providerPaymentId,
                Instant.ofEpochSecond(event.getCreated())
        );
    }
}
