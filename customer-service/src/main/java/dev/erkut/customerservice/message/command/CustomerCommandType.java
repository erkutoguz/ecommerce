package dev.erkut.customerservice.message.command;

public enum CustomerCommandType {
    CREATE_CUSTOMER_COMMAND;

    public static CustomerCommandType from(String s) {
        if (s == null) {
            return null;
        }

        try {
            return CustomerCommandType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
