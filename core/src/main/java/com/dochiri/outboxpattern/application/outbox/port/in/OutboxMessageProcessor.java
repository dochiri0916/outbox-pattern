package com.dochiri.outboxpattern.application.outbox.port.in;

@FunctionalInterface
public interface OutboxMessageProcessor {

    void process(OutboxMessage message);
}
