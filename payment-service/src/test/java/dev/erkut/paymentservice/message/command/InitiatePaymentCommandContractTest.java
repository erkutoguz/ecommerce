package dev.erkut.paymentservice.message.command;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitiatePaymentCommandContractTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");

    @Test
    void initiatePaymentCommand_shouldRoundTripCanonicalWireShape() throws Exception {
        JsonMapper jsonMapper = new JsonMapper();
        InitiatePaymentCommand command = new InitiatePaymentCommand(
                ORDER_ID,
                new BigDecimal("123.45"),
                Currency.TRY
        );

        JsonNode payload = jsonMapper.valueToTree(command);
        InitiatePaymentCommand deserialized = jsonMapper.treeToValue(payload, InitiatePaymentCommand.class);

        assertEquals(3, payload.size());
        assertEquals(ORDER_ID.toString(), payload.get("orderId").asString());
        assertEquals(0, new BigDecimal("123.45").compareTo(payload.get("totalAmount").decimalValue()));
        assertEquals("TRY", payload.get("currency").asString());
        assertEquals(command, deserialized);
    }
}
