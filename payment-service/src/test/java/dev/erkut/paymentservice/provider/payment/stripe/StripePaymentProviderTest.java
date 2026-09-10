package dev.erkut.paymentservice.provider.payment.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiMode;
import com.stripe.net.ApiResource;
import com.stripe.net.BaseAddress;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponseGetter;
import dev.erkut.paymentservice.payment.domain.Currency;
import dev.erkut.paymentservice.provider.payment.PaymentSession;
import dev.erkut.paymentservice.provider.payment.exception.PaymentProviderException;
import dev.erkut.paymentservice.provider.payment.stripe.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripePaymentProviderTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final StripeProperties PROPERTIES = new StripeProperties(
            "test-secret",
            "test-whsec",
            "http://localhost:3000/payment/success",
            "http://localhost:3000/payment/cancel"
    );

    @Test
    void initializePaymentSession_shouldMapStripeSessionAndBuildDeterministicCheckoutRequest() throws Exception {
        StripeResponseGetter responseGetter = mock(StripeResponseGetter.class, CALLS_REAL_METHODS);
        Session session = new Session();
        session.setId("cs_test_123");
        session.setUrl("https://checkout.stripe.com/test");
        when(responseGetter.request(
                any(BaseAddress.class),
                any(ApiResource.RequestMethod.class),
                anyString(),
                anyMap(),
                any(Type.class),
                any(RequestOptions.class),
                any(ApiMode.class)
        )).thenReturn(session);
        StripePaymentProvider provider = new StripePaymentProvider(new StripeClient(responseGetter), PROPERTIES);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);

        PaymentSession result = provider.initializePaymentSession(
                ORDER_ID,
                new BigDecimal("123.45"),
                Currency.TRY
        );
        provider.initializePaymentSession(ORDER_ID, new BigDecimal("123.45"), Currency.TRY);

        verify(responseGetter, times(2)).request(
                eq(BaseAddress.API),
                eq(ApiResource.RequestMethod.POST),
                eq("/v1/checkout/sessions"),
                paramsCaptor.capture(),
                eq(Session.class),
                optionsCaptor.capture(),
                any(ApiMode.class)
        );
        List<Map<String, Object>> parameterSets = paramsCaptor.getAllValues();
        List<RequestOptions> requestOptions = optionsCaptor.getAllValues();
        Map<String, Object> params = parameterSets.getFirst();
        Map<String, Object> lineItem = ((List<Map<String, Object>>) params.get("line_items")).getFirst();
        Map<String, Object> priceData = (Map<String, Object>) lineItem.get("price_data");

        assertEquals(new PaymentSession("cs_test_123", "https://checkout.stripe.com/test"), result);
        assertEquals("payment", params.get("mode"));
        assertEquals(PROPERTIES.successUrl(), params.get("success_url"));
        assertEquals(PROPERTIES.cancelUrl(), params.get("cancel_url"));
        assertEquals(ORDER_ID.toString(), params.get("client_reference_id"));
        assertEquals(1L, lineItem.get("quantity"));
        assertEquals("try", priceData.get("currency"));
        assertEquals(12345L, priceData.get("unit_amount"));
        assertEquals("payment-initiation:" + ORDER_ID, requestOptions.getFirst().getIdempotencyKey());
        assertEquals(requestOptions.getFirst().getIdempotencyKey(), requestOptions.getLast().getIdempotencyKey());
    }

    @Test
    void initializePaymentSession_shouldWrapStripeException() throws Exception {
        StripeResponseGetter responseGetter = mock(StripeResponseGetter.class, CALLS_REAL_METHODS);
        ApiException stripeException = new ApiException("Stripe unavailable", null, null, 500, null);
        when(responseGetter.request(
                any(BaseAddress.class),
                any(ApiResource.RequestMethod.class),
                anyString(),
                anyMap(),
                any(Type.class),
                any(RequestOptions.class),
                any(ApiMode.class)
        )).thenThrow(stripeException);
        StripePaymentProvider provider = new StripePaymentProvider(new StripeClient(responseGetter), PROPERTIES);

        PaymentProviderException exception = assertThrows(
                PaymentProviderException.class,
                () -> provider.initializePaymentSession(ORDER_ID, new BigDecimal("123.45"), Currency.TRY)
        );

        assertSame(stripeException, exception.getCause());
    }
}
