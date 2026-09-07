package dev.erkut.stockservice.message.event;

import java.time.Instant;
import java.util.UUID;

public record StockReservedEvent(
        UUID orderId,
        Instant reservedAt
){}
