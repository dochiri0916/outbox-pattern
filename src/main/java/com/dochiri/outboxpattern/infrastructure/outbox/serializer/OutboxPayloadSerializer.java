package com.dochiri.outboxpattern.infrastructure.outbox.serializer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

@Component
@RequiredArgsConstructor
public class OutboxPayloadSerializer {

    private final ObjectMapper objectMapper;

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Payload serialization failed", e);
        }
    }

    public <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (MismatchedInputException e) {
            try {
                String unwrappedPayload = objectMapper.readValue(payload, String.class);
                return objectMapper.readValue(unwrappedPayload, type);
            } catch (Exception fallbackException) {
                throw new IllegalStateException("Payload deserialization failed", fallbackException);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Payload deserialization failed", e);
        }
    }

}
