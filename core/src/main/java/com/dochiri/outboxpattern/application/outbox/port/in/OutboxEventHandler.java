package com.dochiri.outboxpattern.application.outbox.port.in;

public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(OutboxProcessingContext context);
}
