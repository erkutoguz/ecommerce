package dev.erkut.stockservice.message;

import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.message.event.ProductDeactivatedEvent;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.event.StockReservationConfirmedEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageContractTest {

    @Test
    void productCreatedEventMatchesProducerWireShape() throws Exception {
        UUID productId = UUID.fromString("90000000-0000-0000-0000-000000000001");
        JsonMapper mapper = new JsonMapper();

        var payload = mapper.valueToTree(new ProductCreatedEvent(productId));

        assertEquals(1, payload.size());
        assertEquals(productId.toString(), payload.get("productId").asText());
        assertEquals(productId,
                mapper.treeToValue(payload, ProductCreatedEvent.class).productId());
    }

    @Test
    void productDeactivatedEventMatchesProducerWireShape() throws Exception {
        UUID productId = UUID.fromString("90000000-0000-0000-0000-000000000001");
        JsonMapper mapper = new JsonMapper();

        var payload = mapper.readTree("{\"productId\":\"" + productId + "\"}");

        assertEquals(1, payload.size());
        assertEquals(productId,
                mapper.treeToValue(payload, ProductDeactivatedEvent.class).productId());
    }

    @Test
    void confirmationCommandAndEventContainOnlyOrderId() throws Exception {
        UUID orderId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        JsonMapper mapper = new JsonMapper();

        var commandPayload = mapper.valueToTree(new ConfirmStockReservationCommand(orderId));
        var eventPayload = mapper.valueToTree(new StockReservationConfirmedEvent(orderId));

        assertEquals(1, commandPayload.size());
        assertEquals(1, eventPayload.size());
        assertEquals(orderId.toString(), commandPayload.get("orderId").asText());
        assertEquals(orderId.toString(), eventPayload.get("orderId").asText());
    }
}
