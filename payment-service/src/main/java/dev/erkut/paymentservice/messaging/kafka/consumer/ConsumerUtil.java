package dev.erkut.paymentservice.messaging.kafka.consumer;

import dev.erkut.paymentservice.message.MessageDeserializationException;
import dev.erkut.paymentservice.message.MessageEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ConsumerUtil {
    private final JsonMapper jsonMapper;

    public ConsumerUtil(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> T deserialize(JsonNode payload, Class<T> type) {
        try {
            return jsonMapper.treeToValue(payload, type);
        } catch (JacksonException exception) {
            throw new MessageDeserializationException(
                    "Message payload could not be deserialized",
                    exception
            );
        }
    }

    public void validateEnvelope(MessageEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (envelope.messageId() == null) {
            throw new IllegalArgumentException("Message id cannot be null");
        }
        if (envelope.messageType() == null || envelope.messageType().isBlank()) {
            throw new IllegalArgumentException("Message type cannot be null or blank");
        }
        if (envelope.occurredAt() == null) {
            throw new IllegalArgumentException("Occurrence time cannot be null");
        }
        if (envelope.payload() == null) {
            throw new IllegalArgumentException("Message payload cannot be null");
        }
    }
}
