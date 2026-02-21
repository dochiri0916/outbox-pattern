package com.dochiri.outboxpattern.infrastructure.outbox.handler;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxEventContext;

public interface OutboxEventHandler {

    boolean supports(OutboxEventType eventType);

    void handle(OutboxEventContext eventContext);

}
