package dev.erkut.stockservice.outbox.application;

import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import java.util.UUID;

public record ClaimedOutboxMessage(OutboxMessage message, UUID claimToken) {}
