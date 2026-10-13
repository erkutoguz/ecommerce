package dev.erkut.paymentservice.outbox.application;

import dev.erkut.paymentservice.outbox.domain.OutboxMessage;
import java.util.UUID;

public record ClaimedOutboxMessage(OutboxMessage message, UUID claimToken) {}
