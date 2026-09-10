package dev.erkut.orderworkflowservice.message.event;

public enum PaymentEventType {
    PAYMENT_COMPLETED_EVENT;

    public static PaymentEventType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return PaymentEventType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
