package dev.erkut.productservice.message;

import dev.erkut.productservice.message.event.ProductCreatedEvent;
import dev.erkut.productservice.message.event.ProductDeactivatedEvent;
import dev.erkut.productservice.message.event.ProductDeletedEvent;
import dev.erkut.productservice.message.event.ProductUpdatedEvent;
import dev.erkut.productservice.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageContractTest {

    private static final UUID PRODUCT_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void productCreatedEventUsesCanonicalWireShape() throws Exception {
        JsonMapper mapper = new JsonMapper();
        ProductCreatedEvent event = new ProductCreatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), ProductStatus.ACTIVE, NOW, NOW);
        var payload = mapper.valueToTree(event);

        assertEquals(6, payload.size());
        assertEquals(PRODUCT_ID.toString(), payload.get("productId").asText());
        assertEquals("Keyboard", payload.get("name").asText());
        assertEquals("99.90", payload.get("price").asText());
        assertEquals("ACTIVE", payload.get("status").asText());
        assertEquals(event, mapper.treeToValue(payload, ProductCreatedEvent.class));
    }

    @Test
    void productDeactivatedEventUsesCanonicalWireShape() throws Exception {
        JsonMapper mapper = new JsonMapper();
        ProductDeactivatedEvent event = new ProductDeactivatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), ProductStatus.INACTIVE, NOW, NOW);
        var payload = mapper.valueToTree(event);

        assertEquals(6, payload.size());
        assertEquals(PRODUCT_ID.toString(), payload.get("productId").asText());
        assertEquals("INACTIVE", payload.get("status").asText());
        assertEquals(event, mapper.treeToValue(payload, ProductDeactivatedEvent.class));
    }

    @Test
    void productUpdatedAndDeletedEventsUseExpectedWireShapes() {
        JsonMapper mapper = new JsonMapper();
        var updated = mapper.valueToTree(new ProductUpdatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("109.00"), ProductStatus.ACTIVE, NOW, NOW));
        var deleted = mapper.valueToTree(new ProductDeletedEvent(PRODUCT_ID));

        assertEquals(6, updated.size());
        assertEquals("109.00", updated.get("price").asText());
        assertEquals(1, deleted.size());
        assertEquals(PRODUCT_ID.toString(), deleted.get("productId").asText());
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
