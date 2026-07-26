package com.dochiri.outboxpattern.cdc.consumer;

import com.dochiri.outboxpattern.application.outbox.port.in.OutboxMessage;

public record DebeziumOutboxRecord(
        String op,
        OutboxMessage after
) {
}
