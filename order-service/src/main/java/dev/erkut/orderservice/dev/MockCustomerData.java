package dev.erkut.orderservice.dev;

import java.util.Set;
import java.util.UUID;

public final class MockCustomerData {

    public static final Set<UUID> CUSTOMER_IDS;

    static {
        CUSTOMER_IDS = Set.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        );
    }


}
