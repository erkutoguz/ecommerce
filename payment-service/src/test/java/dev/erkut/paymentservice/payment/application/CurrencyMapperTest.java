package dev.erkut.paymentservice.payment.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyMapperTest {

    @ParameterizedTest
    @EnumSource(dev.erkut.paymentservice.message.command.Currency.class)
    void from_shouldMapEverySupportedCommandCurrency(
            dev.erkut.paymentservice.message.command.Currency commandCurrency
    ) {
        dev.erkut.paymentservice.payment.domain.Currency domainCurrency = CurrencyMapper.from(commandCurrency);

        assertEquals(commandCurrency.name(), domainCurrency.name());
    }

    @Test
    void from_shouldRejectNullCurrency() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyMapper.from(null));
    }
}
