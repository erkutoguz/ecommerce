package dev.erkut.orderworkflowservice.message.event.orderevents;

public enum OrderEventType {
    ORDER_CHECKOUT_STARTED,
    ORDER_REJECTED_EVENT,
    ORDER_CONFIRMED_EVENT;

    public static OrderEventType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return OrderEventType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
