package dev.erkut.orderworkflowservice.saga.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyMapperTest {

    @ParameterizedTest
    @EnumSource(dev.erkut.orderworkflowservice.message.event.Currency.class)
    void currencyMappings_shouldPreserveEveryWireValue(
            dev.erkut.orderworkflowservice.message.event.Currency eventCurrency
    ) {
        dev.erkut.orderworkflowservice.saga.domain.Currency domainCurrency =
                CurrencyMapper.from(eventCurrency);

        assertEquals(eventCurrency.name(), domainCurrency.name());
        assertEquals(eventCurrency.name(), CurrencyMapper.toCommand(domainCurrency).name());
    }

    @Test
    void currencyMappings_shouldRejectNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CurrencyMapper.from(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CurrencyMapper.toCommand(null)
        );
    }
}
