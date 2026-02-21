package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;

public record OutboxEventContext(Long id, OutboxEventType eventType, String payload) {

}
