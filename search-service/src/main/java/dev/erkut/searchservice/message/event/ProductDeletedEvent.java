package dev.erkut.searchservice.message.event;

import java.util.UUID;

public record ProductDeletedEvent(UUID productId) {}
