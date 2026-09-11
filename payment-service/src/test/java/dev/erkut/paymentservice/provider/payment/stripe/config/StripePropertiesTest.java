package dev.erkut.paymentservice.provider.payment.stripe.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripePropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validator = null;
    }

    @ParameterizedTest
    @ValueSource(longs = {29, 1441})
    void checkoutExpirationMinutesOutsideStripeBoundsIsRejected(long minutes) {
        StripeProperties properties = properties(minutes);

        assertFalse(validator.validate(properties).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {30, 1440})
    void checkoutExpirationMinutesAtStripeBoundsIsAccepted(long minutes) {
        StripeProperties properties = properties(minutes);

        assertTrue(validator.validate(properties).isEmpty());
    }

    private static StripeProperties properties(long minutes) {
        return new StripeProperties(
                "test-secret",
                "test-webhook-secret",
                "http://localhost/success",
                "http://localhost/cancel",
                minutes
        );
    }
}
