package com.dochiri.outboxpattern.application.outbox.port.in;

public record OutboxMessage(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload
) {
}
