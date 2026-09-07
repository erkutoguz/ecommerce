package dev.erkut.productservice.message.event;

import java.util.UUID;

public record ProductDeactivatedEvent(
   UUID productId
) {}
