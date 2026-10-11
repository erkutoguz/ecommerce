package dev.erkut.productservice.messaging.kafka.routing;

import dev.erkut.productservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.productservice.outbox.domain.OutboxMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicResolverTest {
    @Test
    void allProductLifecycleEventsUseProductEventsTopic() {
        KafkaTopicResolver resolver = new KafkaTopicResolver(new KafkaTopicsProperties("product.events"));

        assertEquals("product.events", resolver.resolve(OutboxMessageType.PRODUCT_CREATED_EVENT));
        assertEquals("product.events", resolver.resolve(OutboxMessageType.PRODUCT_UPDATED_EVENT));
        assertEquals("product.events", resolver.resolve(OutboxMessageType.PRODUCT_DEACTIVATED_EVENT));
        assertEquals("product.events", resolver.resolve(OutboxMessageType.PRODUCT_DELETED_EVENT));
    }
}
