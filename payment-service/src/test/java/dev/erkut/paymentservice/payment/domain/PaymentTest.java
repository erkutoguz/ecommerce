package dev.erkut.paymentservice.payment.domain;

import dev.erkut.paymentservice.payment.domain.exception.InvalidPaymentStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void create_shouldInitializeProcessingPayment() {
        Payment payment = Payment.create(
                ORDER_ID,
                new BigDecimal("123.45"),
                Currency.TRY,
                CREATED_AT
        );

        assertEquals(ORDER_ID, payment.getOrderId());
        assertEquals(0, new BigDecimal("123.45").compareTo(payment.getTotalAmount()));
        assertEquals(Currency.TRY, payment.getCurrency());
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        assertEquals(CREATED_AT, payment.getCreatedAt());
        assertEquals(CREATED_AT, payment.getUpdatedAt());
        assertNull(payment.getProcessedAt());
        assertNull(payment.getProviderPaymentId());
        assertNull(payment.getCheckoutUrl());
    }

    @ParameterizedTest
    @MethodSource("invalidCreationArguments")
    void create_shouldRejectInvalidArguments(
            UUID orderId,
            BigDecimal totalAmount,
            Currency currency,
            Instant createdAt
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> Payment.create(orderId, totalAmount, currency, createdAt)
        );
    }

    @Test
    void markAwaitingCustomerAction_shouldSetProviderDetailsWithoutProcessingPayment() {
        Payment payment = payment();
        Instant updatedAt = CREATED_AT.plusSeconds(5);

        payment.markAwaitingCustomerAction(
                "cs_test_123",
                "https://checkout.stripe.com/test",
                updatedAt
        );

        assertEquals(PaymentStatus.AWAITING_CUSTOMER_ACTION, payment.getStatus());
        assertEquals("cs_test_123", payment.getProviderPaymentId());
        assertEquals("https://checkout.stripe.com/test", payment.getCheckoutUrl());
        assertEquals(updatedAt, payment.getUpdatedAt());
        assertNull(payment.getProcessedAt());
    }

    @ParameterizedTest
    @MethodSource("invalidAwaitingCustomerActionArguments")
    void markAwaitingCustomerAction_shouldRejectInvalidArguments(
            String providerPaymentId,
            String checkoutUrl,
            Instant updatedAt
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> payment().markAwaitingCustomerAction(providerPaymentId, checkoutUrl, updatedAt)
        );
    }

    @Test
    void markAwaitingCustomerAction_shouldRejectTransitionFromNonProcessingState() {
        Payment payment = payment();
        payment.markAwaitingCustomerAction(
                "cs_test_123",
                "https://checkout.stripe.com/test",
                CREATED_AT.plusSeconds(1)
        );

        assertThrows(
                InvalidPaymentStateException.class,
                () -> payment.markAwaitingCustomerAction(
                        "cs_test_456",
                        "https://checkout.stripe.com/test-2",
                        CREATED_AT.plusSeconds(2)
                )
        );
    }

    @Test
    void markCompleted_shouldSetCompletedStateAndTimestamps() {
        Payment payment = payment();
        payment.markAwaitingCustomerAction(
                "cs_test_123",
                "https://checkout.stripe.com/test",
                CREATED_AT.plusSeconds(1)
        );

        Instant occurredAt = CREATED_AT.plusSeconds(10);
        Instant updatedAt = CREATED_AT.plusSeconds(11);
        payment.markCompleted(occurredAt, updatedAt);

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(occurredAt, payment.getProcessedAt());
        assertEquals(updatedAt, payment.getUpdatedAt());
    }

    @Test
    void markCompleted_shouldRejectCompletionFromProcessingState() {
        assertThrows(
                InvalidPaymentStateException.class,
                () -> payment().markCompleted(CREATED_AT.plusSeconds(1), CREATED_AT.plusSeconds(2))
        );
    }

    @Test
    void markCompleted_shouldRejectNullTimestamps() {
        Payment payment = payment();
        payment.markAwaitingCustomerAction(
                "cs_test_123",
                "https://checkout.stripe.com/test",
                CREATED_AT.plusSeconds(1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> payment.markCompleted(null, CREATED_AT.plusSeconds(2))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> payment.markCompleted(CREATED_AT.plusSeconds(2), null)
        );
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> invalidCreationArguments() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(null, new BigDecimal("1.00"), Currency.TRY, CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of(ORDER_ID, null, Currency.TRY, CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of(ORDER_ID, BigDecimal.ZERO, Currency.TRY, CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of(ORDER_ID, new BigDecimal("-0.01"), Currency.TRY, CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of(ORDER_ID, new BigDecimal("1.00"), null, CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of(ORDER_ID, new BigDecimal("1.00"), Currency.TRY, null)
        );
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> invalidAwaitingCustomerActionArguments() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(null, "https://checkout.stripe.com/test", CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of(" ", "https://checkout.stripe.com/test", CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of("cs_test_123", null, CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of("cs_test_123", " ", CREATED_AT),
                org.junit.jupiter.params.provider.Arguments.of("cs_test_123", "https://checkout.stripe.com/test", null)
        );
    }

    private static Payment payment() {
        return Payment.create(ORDER_ID, new BigDecimal("123.45"), Currency.TRY, CREATED_AT);
    }
}
