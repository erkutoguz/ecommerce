package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;

import java.util.List;

public class OutboxRelay {

    private final OutboxMessageRepository outboxMessageRepository;

    public OutboxRelay(OutboxMessageRepository outboxMessageRepository) {
        this.outboxMessageRepository = outboxMessageRepository;
    }

    public void relay() {
        List<OutboxMessage> messages = outboxMessageRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for(OutboxMessage message : messages) {
            publish(message);
        }
    }

    private void publish(OutboxMessage message) {

    }
}


