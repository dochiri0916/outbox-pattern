package com.dochiri.outboxpattern.infrastructure.outbox.worker;

public record OutboxEventContext(Long id, String eventType, String payload) {

}
