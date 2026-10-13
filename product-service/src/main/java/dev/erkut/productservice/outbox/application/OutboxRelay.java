package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.message.MessageEnvelope;
import dev.erkut.productservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.productservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.productservice.observability.tracing.OutboxTraceContext;
import dev.erkut.productservice.outbox.domain.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    // Kafka delivery.timeout.ms is set to 120s; grace covers scheduling and DB completion.
    private static final long CLAIM_LEASE_SECONDS = 180;

    private final OutboxService outboxService;
    private final KafkaMessagePublisher messagePublisher;
    private final KafkaTopicResolver topicResolver;
    private final OutboxTraceContext outboxTraceContext;

    public OutboxRelay(OutboxService outboxService, KafkaMessagePublisher messagePublisher,
                       KafkaTopicResolver topicResolver, OutboxTraceContext outboxTraceContext) {
        this.outboxService = outboxService;
        this.messagePublisher = messagePublisher;
        this.topicResolver = topicResolver;
        this.outboxTraceContext = outboxTraceContext;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-relay-ms}")
    public void relay() {
        List<ClaimedOutboxMessage> claims = outboxService.claimMessages(BATCH_SIZE, CLAIM_LEASE_SECONDS);
        List<PendingPublish> sends = new ArrayList<>(claims.size());
        for (ClaimedOutboxMessage claim : claims) {
            try {
                sends.add(startPublish(claim));
            } catch (RuntimeException exception) {
                scheduleRetry(claim, exception);
            }
        }
        // Sends are started before waiting, so each row's lease covers a bounded Kafka send, not a sequential batch.
        for (PendingPublish send : sends) {
            try {
                send.ack().join();
                complete(send.claim());
            } catch (CompletionException exception) {
                scheduleRetry(send.claim(), exception);
            }
        }
    }

    private PendingPublish startPublish(ClaimedOutboxMessage claim) {
        OutboxMessage message = claim.message();
        MessageEnvelope envelope = new MessageEnvelope(
                message.getId(), message.getMessageType().name(), message.getCreatedAt(), message.getPayload());
        String topic = topicResolver.resolve(message.getMessageType());
        OutboxTraceContext.Headers parent = message.getTraceparent() == null ? null
                : new OutboxTraceContext.Headers(message.getTraceparent(), message.getTracestate());
        AtomicReference<CompletableFuture<?>> ack = new AtomicReference<>();
        outboxTraceContext.runWithParent(parent,
                () -> ack.set(messagePublisher.publish(topic, message.getAggregateId(), envelope)));
        return new PendingPublish(claim, ack.get());
    }

    private void complete(ClaimedOutboxMessage claim) {
        try {
            if (!outboxService.markPublished(claim.message().getId(), claim.claimToken(), Instant.now())) {
                log.warn("Outbox claim was superseded before completion: messageId={}", claim.message().getId());
            }
        } catch (RuntimeException exception) {
            // Kafka ACKed; lease expiry makes the row retryable if database completion failed.
            log.error("Kafka ACK received but outbox completion failed; lease recovery will retry: messageId={}",
                    claim.message().getId(), exception);
        }
    }

    private void scheduleRetry(ClaimedOutboxMessage claim, RuntimeException exception) {
        long delaySeconds = Math.min(300, 1L << Math.min(claim.message().getAttemptCount(), 9));
        try {
            if (!outboxService.reschedule(claim.message().getId(), claim.claimToken(),
                    Instant.now().plusSeconds(delaySeconds))) {
                log.warn("Outbox claim was superseded after publish failure: messageId={}", claim.message().getId());
            }
        } catch (RuntimeException persistenceException) {
            exception.addSuppressed(persistenceException);
            log.error("Outbox retry scheduling failed; lease recovery will retry: messageId={}",
                    claim.message().getId(), exception);
        }
        log.error("Outbox publish failed; retry scheduled: messageId={}, attempt={}",
                claim.message().getId(), claim.message().getAttemptCount(), exception);
    }

    private record PendingPublish(ClaimedOutboxMessage claim, CompletableFuture<?> ack) {}
}
