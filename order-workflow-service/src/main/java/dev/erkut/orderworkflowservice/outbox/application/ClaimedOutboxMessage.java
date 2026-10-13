package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import java.util.UUID;

public record ClaimedOutboxMessage(OutboxMessage message, UUID claimToken) {}
