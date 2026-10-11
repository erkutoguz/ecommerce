package dev.erkut.searchservice.application;

import java.util.Locale;

public enum ProductSearchSort {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NEWEST;

    public static ProductSearchSort fromParameter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "relevance" -> RELEVANCE;
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            case "newest" -> NEWEST;
            default -> throw new IllegalArgumentException("Unsupported sort value: " + value);
        };
    }
}
