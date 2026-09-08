package dev.erkut.orderworkflowservice.message.event;

public enum StockEventType {
    STOCK_RESERVED_EVENT,
    STOCK_RESERVATION_FAILED_EVENT;

    public static StockEventType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return StockEventType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
