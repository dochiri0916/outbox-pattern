package com.dochiri.outboxpattern.infrastructure.outbox.handler;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;

public interface OutboxEventHandler {

    boolean supports(OutboxEventType eventType);

    void handle(OutboxEvent event);

}