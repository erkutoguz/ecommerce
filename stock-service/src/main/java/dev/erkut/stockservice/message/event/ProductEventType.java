package dev.erkut.stockservice.message.event;

public enum ProductEventType {
    PRODUCT_CREATED;

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
