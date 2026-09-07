package dev.erkut.stockservice.message.event;

import java.util.UUID;

public record ProductCreatedEvent(
   UUID productId
) {}
