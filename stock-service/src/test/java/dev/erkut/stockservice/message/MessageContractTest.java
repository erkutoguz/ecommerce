package dev.erkut.stockservice.message;

import dev.erkut.stockservice.message.event.ProductCreatedEvent;
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
}
