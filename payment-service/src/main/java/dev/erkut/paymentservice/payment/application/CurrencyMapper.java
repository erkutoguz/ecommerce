package dev.erkut.paymentservice.payment.application;

import dev.erkut.paymentservice.payment.domain.Currency;

public final class CurrencyMapper {

    private CurrencyMapper() {
    }

    public static Currency from(
            dev.erkut.paymentservice.message.command.Currency currency
    ) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        return switch (currency) {
            case TRY -> Currency.TRY;
            case USD -> Currency.USD;
            case EUR -> Currency.EUR;
        };
    }
}
