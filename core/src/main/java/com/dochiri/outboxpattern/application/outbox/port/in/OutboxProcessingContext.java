package com.dochiri.outboxpattern.application.outbox.port.in;

public record OutboxProcessingContext(
        Long id,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload,
        String processingOwnerId
) {
}
