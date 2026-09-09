package dev.erkut.paymentservice.provider.payment.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import dev.erkut.paymentservice.payment.domain.Currency;
import dev.erkut.paymentservice.provider.payment.PaymentProvider;
import dev.erkut.paymentservice.provider.payment.PaymentSession;
import dev.erkut.paymentservice.provider.payment.exception.PaymentProviderException;
import dev.erkut.paymentservice.provider.payment.stripe.config.StripeProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class StripePaymentProvider implements PaymentProvider {

    private final StripeClient stripeClient;
    private final StripeProperties properties;

    public StripePaymentProvider(
            StripeClient stripeClient,
            StripeProperties properties
    ) {
        this.stripeClient = stripeClient;
        this.properties = properties;
    }

    @Override
    public PaymentSession initializePaymentSession(
            UUID orderId,
            BigDecimal totalAmount,
            Currency currency
    ) {
        try {
            Session session =
                    createCheckoutSessionObject(orderId, totalAmount, currency);

            return new PaymentSession(
                    session.getId(),
                    session.getUrl()
            );

        } catch (StripeException e) {
            throw new PaymentProviderException("Failed to initialize Stripe checkout session", e);
        }}

    private Session createCheckoutSessionObject(
            UUID orderId,
            BigDecimal totalAmount,
            Currency currency
    ) throws StripeException {

        long unitAmount = totalAmount
                .movePointRight(2)
                .longValueExact();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency.name().toLowerCase())
                        .setUnitAmount(unitAmount)
                        .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Order " + orderId)
                                        .build()
                        )
                        .build();

        SessionCreateParams.LineItem item =
                SessionCreateParams.LineItem.builder()
                        .setPriceData(priceData)
                        .setQuantity(1L)
                        .build();

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(properties.successUrl())
                        .setCancelUrl(properties.cancelUrl())
                        .setClientReferenceId(orderId.toString())
                        .addLineItem(item)
                        .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("payment-initiation:" + orderId)
                .build();

        return stripeClient
                .v1()
                .checkout()
                .sessions()
                .create(params, requestOptions);
    }
}
