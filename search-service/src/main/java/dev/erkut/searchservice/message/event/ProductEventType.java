package dev.erkut.searchservice.message.event;

public enum ProductEventType {
    PRODUCT_CREATED_EVENT,
    PRODUCT_UPDATED_EVENT,
    PRODUCT_DEACTIVATED_EVENT,
    PRODUCT_DELETED_EVENT;

    public static ProductEventType from(String value) {
        if (value == null) {
            return null;
        }

        try {
            return ProductEventType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
