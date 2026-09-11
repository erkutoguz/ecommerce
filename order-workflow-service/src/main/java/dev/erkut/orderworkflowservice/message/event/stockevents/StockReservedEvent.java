package dev.erkut.orderworkflowservice.message.event.stockevents;

import java.time.Instant;
import java.util.UUID;

public record StockReservedEvent(
        UUID orderId,
        Instant reservedAt
) {}
