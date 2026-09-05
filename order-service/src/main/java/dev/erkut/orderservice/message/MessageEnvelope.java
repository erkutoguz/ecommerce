package dev.erkut.orderservice.message;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record MessageEnvelope(
        UUID messageId,
        String messageType,
        Instant occurredAt,
        JsonNode payload

) {}
