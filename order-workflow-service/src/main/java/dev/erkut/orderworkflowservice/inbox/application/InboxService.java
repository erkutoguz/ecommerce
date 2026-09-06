package dev.erkut.orderworkflowservice.inbox.application;

import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InboxService {

    private final InboxMessageRepository inboxRepository;

    public InboxService(InboxMessageRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    @Transactional
    public boolean tryRegister(
            UUID messageId,
            String messageType,
            UUID aggregateId,
            Instant processedAt
    ) {
        if (messageId == null) {
            throw new IllegalArgumentException("Message id cannot be null");
        }
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("Message type cannot be null or blank");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("Aggregate id cannot be null");
        }
        if (processedAt == null) {
            throw new IllegalArgumentException("Processing time cannot be null");
        }

        return inboxRepository.insertIfAbsent(
                messageId,
                messageType,
                aggregateId,
                processedAt
        ) == 1;
    }
}
