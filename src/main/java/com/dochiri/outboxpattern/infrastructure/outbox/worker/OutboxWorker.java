package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;
import com.dochiri.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxStatusService outboxStatusService;
    private final List<OutboxEventHandler> outboxEventHandlers;
    private final OutboxEventRepository outboxEventRepository;

    public void runOnce() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findNextBatch(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (OutboxEvent pendingEvent : pendingEvents) {
            process(pendingEvent.getId());
        }
    }

    private void process(Long outboxEventId) {
        OutboxEvent processingEvent = outboxStatusService.markProcessing(outboxEventId);

        if (processingEvent == null) {
            return;
        }

        try {
            OutboxEventHandler handler = findHandler(processingEvent.getEventType());

            handler.handle(processingEvent);
            outboxStatusService.markCompleted(processingEvent.getId());
        } catch (Exception e) {
            outboxStatusService.markFailed(processingEvent.getId(), MAX_RETRY_COUNT);
        }

        OutboxEventHandler handler = findHandler(processingEvent.getEventType());
        handler.handle(processingEvent);
    }

    private OutboxEventHandler findHandler(OutboxEventType eventType) {
        for (OutboxEventHandler handler : outboxEventHandlers) {
            if (handler.supports(eventType)) {
                return handler;
            }
        }
        throw new IllegalStateException("No handler found for event type: " + eventType);
    }

}