package dev.erkut.paymentservice.provider.payment.stripe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "payment.stripe")
public record StripeProperties(
        String secretKey,
        String webhookSecret,
        String successUrl,
        String cancelUrl,
        @Min(30)
        @Max(1440)
        long checkoutExpirationMinutes
) {
}
