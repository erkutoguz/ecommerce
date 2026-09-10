package dev.erkut.stockservice.messaging.kafka.routing;

import dev.erkut.stockservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicResolverTest {

    @Test
    void confirmedReservationEventRoutesToStockEventsTopic() {
        KafkaTopicResolver resolver = new KafkaTopicResolver(new KafkaTopicsProperties(
                "product.events",
                "stock.commands",
                "stock.events",
                "stock.commands.DLT",
                "product.events.DLT"
        ));

        assertEquals("stock.events",
                resolver.resolve(OutboxMessageType.STOCK_RESERVATION_CONFIRMED_EVENT));
    }
}
