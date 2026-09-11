package dev.erkut.stockservice.message.command;


public enum StockCommandType {
    RESERVE_STOCK_COMMAND,
    CONFIRM_STOCK_RESERVATION_COMMAND,
    RELEASE_STOCK_RESERVATION_COMMAND;

    public static StockCommandType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return StockCommandType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
