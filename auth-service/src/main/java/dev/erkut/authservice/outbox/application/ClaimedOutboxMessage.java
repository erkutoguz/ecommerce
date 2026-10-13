package dev.erkut.authservice.outbox.application;

import dev.erkut.authservice.outbox.domain.OutboxMessage;
import java.util.UUID;

public record ClaimedOutboxMessage(OutboxMessage message, UUID claimToken) {}
