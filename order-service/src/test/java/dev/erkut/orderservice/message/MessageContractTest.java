package dev.erkut.orderservice.message;

import dev.erkut.orderservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderservice.message.command.ConfirmOrderCommand;
import dev.erkut.orderservice.message.command.MarkOrderPaymentCompletedCommand;
import dev.erkut.orderservice.message.command.MarkOrderStockReservedCommand;
import dev.erkut.orderservice.message.command.RejectOrderCommand;
import dev.erkut.orderservice.message.event.OrderConfirmedEvent;
import dev.erkut.orderservice.message.event.OrderRejectedEvent;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
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
    void orderCheckoutStartedEvent_shouldSerializeCanonicalWireShape() {
        OrderCheckoutStartedEvent event = new OrderCheckoutStartedEvent(
                ORDER_ID,
                CUSTOMER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                List.of(new OrderCheckoutStartedEvent.OrderCheckoutItem(PRODUCT_ID, 2))
        );

        JsonNode payload = new JsonMapper().valueToTree(event);

        assertEquals(5, payload.size());
        assertEquals(ORDER_ID.toString(), payload.get("orderId").asText());
        assertEquals(CUSTOMER_ID.toString(), payload.get("customerId").asText());
        assertEquals(new BigDecimal("200.00"), payload.get("totalAmount").decimalValue());
        assertEquals("TRY", payload.get("currency").asText());
        assertEquals(1, payload.get("items").size());
        assertEquals(PRODUCT_ID.toString(), payload.get("items").get(0).get("productId").asText());
        assertEquals(2, payload.get("items").get(0).get("quantity").asInt());
    }

    @Test
    void rejectionCommandAndEvent_shouldKeepCanonicalWireShapesAndEnumVocabulary() throws Exception {
        JsonMapper mapper = new JsonMapper();
        RejectOrderCommand command = new RejectOrderCommand(
                ORDER_ID,
                OrderRejectionReason.OUT_OF_STOCK
        );

        JsonNode commandJson = mapper.valueToTree(command);
        RejectOrderCommand deserializedCommand = mapper.treeToValue(commandJson, RejectOrderCommand.class);
        JsonNode eventJson = mapper.valueToTree(new OrderRejectedEvent(ORDER_ID));
        OrderRejectedEvent deserializedEvent = mapper.treeToValue(eventJson, OrderRejectedEvent.class);

        assertEquals(2, commandJson.size());
        assertEquals(ORDER_ID.toString(), commandJson.get("orderId").asText());
        assertEquals("OUT_OF_STOCK", commandJson.get("rejectionReason").asText());
        assertEquals(command, deserializedCommand);
        assertEquals(1, eventJson.size());
        assertEquals(ORDER_ID.toString(), eventJson.get("orderId").asText());
        assertEquals(new OrderRejectedEvent(ORDER_ID), deserializedEvent);
        assertArrayEquals(
                new OrderRejectionReason[]{
                        OrderRejectionReason.OUT_OF_STOCK,
                        OrderRejectionReason.PAYMENT_DECLINED,
                        OrderRejectionReason.USER_CANCELLED,
                        OrderRejectionReason.RESERVATION_EXPIRED
                },
                OrderRejectionReason.values()
        );
    }

    @Test
    void confirmationCommandsAndEvent_shouldContainOnlyOrderId() {
        JsonMapper mapper = new JsonMapper();

        assertMinimalOrderIdPayload(mapper.valueToTree(new MarkOrderStockReservedCommand(ORDER_ID)));
        assertMinimalOrderIdPayload(mapper.valueToTree(new MarkOrderPaymentCompletedCommand(ORDER_ID)));
        assertMinimalOrderIdPayload(mapper.valueToTree(new ConfirmOrderCommand(ORDER_ID)));
        assertMinimalOrderIdPayload(mapper.valueToTree(new OrderConfirmedEvent(ORDER_ID)));
    }

    private static void assertMinimalOrderIdPayload(JsonNode payload) {
        assertEquals(1, payload.size());
        assertEquals(ORDER_ID.toString(), payload.get("orderId").asText());
    }
}
