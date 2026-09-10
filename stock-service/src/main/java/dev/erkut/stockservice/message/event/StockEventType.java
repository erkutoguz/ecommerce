package dev.erkut.stockservice.message.event;

public enum StockEventType {
    STOCK_RESERVATION_CONFIRMED_EVENT;

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
