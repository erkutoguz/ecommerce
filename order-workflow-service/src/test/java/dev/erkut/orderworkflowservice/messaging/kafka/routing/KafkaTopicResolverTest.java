package dev.erkut.orderworkflowservice.messaging.kafka.routing;

import dev.erkut.orderworkflowservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicResolverTest {

    private final KafkaTopicsProperties topics = new KafkaTopicsProperties(
            "order.events",
            "stock.commands",
            "stock.events",
            "payment.commands",
            "payment.events",
            "payment.events.DLT",
            "order.commands",
            "order.events.DLT",
            "stock.events.DLT"
    );

    @ParameterizedTest
    @MethodSource("commandTopics")
    void resolve_shouldRouteEachCommandToItsOwnedTopic(
            OutboxMessageType messageType,
            String expectedTopic
    ) {
        KafkaTopicResolver resolver = new KafkaTopicResolver(topics);

        assertEquals(expectedTopic, resolver.resolve(messageType));
    }

    private static Stream<Arguments> commandTopics() {
        return Stream.of(
                Arguments.of(OutboxMessageType.RESERVE_STOCK_COMMAND, "stock.commands"),
                Arguments.of(OutboxMessageType.CONFIRM_STOCK_RESERVATION_COMMAND, "stock.commands"),
                Arguments.of(OutboxMessageType.REJECT_ORDER_COMMAND, "order.commands"),
                Arguments.of(OutboxMessageType.MARK_ORDER_STOCK_RESERVED_COMMAND, "order.commands"),
                Arguments.of(OutboxMessageType.MARK_ORDER_PAYMENT_COMPLETED_COMMAND, "order.commands"),
                Arguments.of(OutboxMessageType.CONFIRM_ORDER_COMMAND, "order.commands"),
                Arguments.of(OutboxMessageType.INITIATE_PAYMENT_COMMAND, "payment.commands")
        );
    }
}
