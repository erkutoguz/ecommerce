package dev.erkut.orderworkflowservice.message;

import dev.erkut.orderworkflowservice.message.command.ReserveStockCommand;
import dev.erkut.orderworkflowservice.message.event.Currency;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.OrderRejectedEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageContractTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");

    @Test
    void messageEnvelope_shouldExposeCanonicalRecordShape() {
        var components = MessageEnvelope.class.getRecordComponents();

        assertArrayEquals(
                new String[]{"messageId", "messageType", "occurredAt", "payload"},
                Arrays.stream(components).map(component -> component.getName()).toArray(String[]::new)
        );
        assertArrayEquals(
                new Class<?>[]{UUID.class, String.class, Instant.class, JsonNode.class},
                Arrays.stream(components).map(component -> component.getType()).toArray(Class<?>[]::new)
        );
    }

    @Test
    void orderCheckoutStartedEvent_shouldDeserializeCanonicalWireShape() throws Exception {
        JsonNode payload = new JsonMapper().readTree("""
                {
                  "orderId": "80000000-0000-0000-0000-000000000001",
                  "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "totalAmount": 200.00,
                  "currency": "TRY",
                  "items": [
                    {
                      "productId": "90000000-0000-0000-0000-000000000001",
                      "quantity": 2
                    }
                  ]
                }
                """);

        OrderCheckoutStartedEvent event = new JsonMapper().treeToValue(
                payload,
                OrderCheckoutStartedEvent.class
        );

        assertEquals(ORDER_ID, event.orderId());
        assertEquals(CUSTOMER_ID, event.customerId());
        assertEquals(0, new BigDecimal("200.00").compareTo(event.totalAmount()));
        assertEquals(Currency.TRY, event.currency());
        assertEquals(
                List.of(new OrderCheckoutStartedEvent.OrderCheckoutItem(PRODUCT_ID, 2)),
                event.items()
        );
    }

    @Test
    void orderRejectedEvent_shouldRoundTripCanonicalWireShape() throws Exception {
        JsonMapper jsonMapper = new JsonMapper();
        OrderRejectedEvent expectedEvent = new OrderRejectedEvent(ORDER_ID);

        JsonNode payload = jsonMapper.valueToTree(expectedEvent);
        OrderRejectedEvent deserializedEvent = jsonMapper.treeToValue(
                payload,
                OrderRejectedEvent.class
        );

        assertEquals(1, payload.size());
        assertEquals(ORDER_ID.toString(), payload.get("orderId").asString());
        assertEquals(expectedEvent, deserializedEvent);
    }

    @Test
    void reserveStockCommand_shouldSerializeCommandSpecificNestedVocabularyWithoutChangingJsonShape() {
        ReserveStockCommand command = new ReserveStockCommand(
                ORDER_ID,
                List.of(new ReserveStockCommand.ReserveStockItem(PRODUCT_ID, 2))
        );

        JsonNode payload = new JsonMapper().valueToTree(command);

        assertEquals(2, payload.size());
        assertEquals(ORDER_ID.toString(), payload.get("orderId").asString());
        assertEquals(PRODUCT_ID.toString(), payload.get("items").get(0).get("productId").asString());
        assertEquals(2, payload.get("items").get(0).get("quantity").asInt());
    }
}
