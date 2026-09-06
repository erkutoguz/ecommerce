package dev.erkut.orderworkflowservice.messaging.kafka.routing;

import dev.erkut.orderworkflowservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicResolverTest {

    @Test
    void resolve_reserveStockCommand_shouldReturnStockCommandsTopic() {
        KafkaTopicResolver resolver = new KafkaTopicResolver(
                new KafkaTopicsProperties("order.events", "stock.commands")
        );

        assertEquals("stock.commands", resolver.resolve(OutboxMessageType.RESERVE_STOCK_COMMAND));
    }
}
