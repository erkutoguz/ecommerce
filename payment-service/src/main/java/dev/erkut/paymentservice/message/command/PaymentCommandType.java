package dev.erkut.paymentservice.message.command;

public enum PaymentCommandType {
    INITIATE_PAYMENT_COMMAND;

    public static PaymentCommandType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return PaymentCommandType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
