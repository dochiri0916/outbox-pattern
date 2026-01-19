package com.example.outboxpattern.infrastructure.outbox.handler;

import com.example.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.example.outboxpattern.infrastructure.outbox.entity.OutboxEventType;

public interface OutboxEventHandler {

    boolean supports(OutboxEventType eventType);

    void handle(OutboxEvent event);

}