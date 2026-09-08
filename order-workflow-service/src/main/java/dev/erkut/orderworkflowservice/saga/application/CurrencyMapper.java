package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.saga.domain.Currency;

public final class CurrencyMapper {

    private CurrencyMapper() {
    }

    public static Currency from(
            dev.erkut.orderworkflowservice.message.event.Currency currency
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

    public static dev.erkut.orderworkflowservice.message.command.Currency toCommand(
            Currency currency
    ) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        return switch (currency) {
            case TRY -> dev.erkut.orderworkflowservice.message.command.Currency.TRY;
            case USD -> dev.erkut.orderworkflowservice.message.command.Currency.USD;
            case EUR -> dev.erkut.orderworkflowservice.message.command.Currency.EUR;
        };
    }
}
