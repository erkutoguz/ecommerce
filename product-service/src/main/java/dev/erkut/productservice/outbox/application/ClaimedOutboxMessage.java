package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.outbox.domain.OutboxMessage;

import java.util.UUID;

public record ClaimedOutboxMessage(OutboxMessage message, UUID claimToken) {}
