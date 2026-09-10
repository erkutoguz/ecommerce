package dev.erkut.orderservice.message.command;

public enum OrderCommandType {
    REJECT_ORDER_COMMAND,
    MARK_ORDER_STOCK_RESERVED_COMMAND,
    MARK_ORDER_PAYMENT_COMPLETED_COMMAND,
    CONFIRM_ORDER_COMMAND;

    public static OrderCommandType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return OrderCommandType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
