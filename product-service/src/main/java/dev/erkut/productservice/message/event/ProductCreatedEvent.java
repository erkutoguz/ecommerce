package dev.erkut.productservice.message.event;

import java.util.UUID;

public record ProductCreatedEvent(
        UUID productId
) {}
