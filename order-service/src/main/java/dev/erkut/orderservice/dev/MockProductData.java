package dev.erkut.orderservice.dev;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockProductData {
    public static final Map<UUID, MockProduct> PRODUCTS = Map.of(
            UUID.fromString("90000000-0000-0000-0000-000000000001"),
            new MockProduct(
                    UUID.fromString("90000000-0000-0000-0000-000000000001"),
                    "Mechanical Keyboard",
                    new BigDecimal("2500.00")
            ),

            UUID.fromString("90000000-0000-0000-0000-000000000002"),
            new MockProduct(
                    UUID.fromString("90000000-0000-0000-0000-000000000002"),
                    "Wireless Mouse",
                    new BigDecimal("900.00")
            )
    );


    public record MockProduct(
            UUID itemId,
            String name,
            BigDecimal price
    ) {}
}
