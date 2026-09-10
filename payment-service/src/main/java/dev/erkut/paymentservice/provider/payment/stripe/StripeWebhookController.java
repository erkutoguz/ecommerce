package dev.erkut.paymentservice.provider.payment.stripe;

import dev.erkut.paymentservice.provider.payment.stripe.exception.StripeWebhookException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments/webhooks/stripe")
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping()
    public ResponseEntity<Void> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        webhookService.handle(payload, signature);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(StripeWebhookException.class)
    public ResponseEntity<Void> handleInvalidWebhook(StripeWebhookException exception) {
        return ResponseEntity.badRequest().build();
    }
}
