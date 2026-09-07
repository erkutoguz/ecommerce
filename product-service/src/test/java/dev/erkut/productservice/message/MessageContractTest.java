package dev.erkut.productservice.message;

import dev.erkut.productservice.message.event.ProductCreatedEvent;
import dev.erkut.productservice.message.event.ProductDeactivatedEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageContractTest {

    private static final UUID PRODUCT_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");

    @Test
    void productCreatedEventUsesCanonicalWireShape() throws Exception {
        JsonMapper mapper = new JsonMapper();
        var payload = mapper.valueToTree(new ProductCreatedEvent(PRODUCT_ID));

        assertEquals(1, payload.size());
        assertEquals(PRODUCT_ID.toString(), payload.get("productId").asText());
        assertEquals(PRODUCT_ID,
                mapper.treeToValue(payload, ProductCreatedEvent.class).productId());
    }

    @Test
    void productDeactivatedEventUsesCanonicalWireShape() throws Exception {
        JsonMapper mapper = new JsonMapper();
        var payload = mapper.valueToTree(new ProductDeactivatedEvent(PRODUCT_ID));

        assertEquals(1, payload.size());
        assertEquals(PRODUCT_ID.toString(), payload.get("productId").asText());
    }

    @Test
    void messageEnvelopeUsesCanonicalComponentOrderAndTypes() {
        var components = MessageEnvelope.class.getRecordComponents();

        assertEquals("messageId", components[0].getName());
        assertEquals("messageType", components[1].getName());
        assertEquals("occurredAt", components[2].getName());
        assertEquals("payload", components[3].getName());
        assertEquals(UUID.class, components[0].getType());
        assertEquals(String.class, components[1].getType());
        assertEquals(Instant.class, components[2].getType());
    }
}
