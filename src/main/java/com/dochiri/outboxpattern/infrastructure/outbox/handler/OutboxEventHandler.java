package com.dochiri.outboxpattern.infrastructure.outbox.handler;

import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxEventContext;

public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(OutboxEventContext eventContext);

}
