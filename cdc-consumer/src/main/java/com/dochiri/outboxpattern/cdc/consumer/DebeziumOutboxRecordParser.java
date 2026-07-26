package com.dochiri.outboxpattern.cdc.consumer;

import com.dochiri.outboxpattern.application.outbox.port.in.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class DebeziumOutboxRecordParser {

    private final ObjectMapper objectMapper;

    public DebeziumOutboxRecord parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            String operation = payload.path("op").asString();
            JsonNode after = payload.get("after");
            if (after == null || after.isNull()) {
                return new DebeziumOutboxRecord(operation, null);
            }
            return new DebeziumOutboxRecord(operation, new OutboxMessage(
                    after.path("id").asString(),
                    after.path("aggregate_type").asString(),
                    after.path("aggregate_id").asString(),
                    after.path("event_type").asString(),
                    after.path("payload").asString()
            ));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Debezium outbox record", exception);
        }
    }
}
