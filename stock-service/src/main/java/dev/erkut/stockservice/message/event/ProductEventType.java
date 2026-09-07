package dev.erkut.stockservice.message.event;

public enum ProductEventType {
    PRODUCT_CREATED_EVENT,
    PRODUCT_DEACTIVATED_EVENT;

    public static ProductEventType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return ProductEventType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
