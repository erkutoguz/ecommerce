package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import java.util.UUID;

public record ClaimedOutboxMessage(OutboxMessage message, UUID claimToken) {}
