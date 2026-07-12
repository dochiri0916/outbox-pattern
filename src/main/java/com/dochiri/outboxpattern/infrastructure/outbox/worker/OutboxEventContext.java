package com.dochiri.outboxpattern.infrastructure.outbox.worker;

public record OutboxEventContext(
        Long id,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload,
        String processingOwnerId
) {
}
