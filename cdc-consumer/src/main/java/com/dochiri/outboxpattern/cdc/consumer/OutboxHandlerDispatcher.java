package com.dochiri.outboxpattern.cdc.consumer;

import com.dochiri.outboxpattern.application.outbox.port.in.OutboxEventHandler;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxMessage;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxMessageProcessor;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxProcessingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxHandlerDispatcher implements OutboxMessageProcessor {

    private final List<OutboxEventHandler> handlers;

    @Override
    public void process(OutboxMessage message) {
        OutboxEventHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(message.eventType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Outbox handler found for event type: " + message.eventType()
                ));

        handler.handle(new OutboxProcessingContext(
                Long.valueOf(message.id()),
                message.aggregateType(),
                Long.valueOf(message.aggregateId()),
                message.eventType(),
                message.payload(),
                "cdc-" + UUID.randomUUID()
        ));
    }
}
