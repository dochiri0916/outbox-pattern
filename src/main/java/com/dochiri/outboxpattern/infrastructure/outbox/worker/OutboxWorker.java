package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxStatusService outboxStatusService;
    private final List<OutboxEventHandler> outboxEventHandlers;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxWorkerProperties properties;

    public void runOnce() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findNextBatch(
                OutboxEventStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, properties.batchSize())
        );

        for (OutboxEvent pendingEvent : pendingEvents) {
            process(pendingEvent.getId());
        }
    }

    public void recoverTimedOutProcessingEvents() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minus(properties.processingTimeout());
        List<OutboxEvent> timedOutEvents = outboxEventRepository.findTimedOutProcessingBatch(
                OutboxEventStatus.PROCESSING,
                timeoutThreshold,
                PageRequest.of(0, properties.batchSize())
        );

        for (OutboxEvent timedOutEvent : timedOutEvents) {
            boolean recovered = outboxStatusService.recoverTimedOutProcessing(
                    timedOutEvent.getId(),
                    timeoutThreshold,
                    properties.maxRetryCount()
            );

            if (recovered) {
                log.warn("Recovered timed out PROCESSING outbox event. eventId={}", timedOutEvent.getId());
            }
        }
    }

    private void process(Long outboxEventId) {
        OutboxEventContext processingEvent = outboxStatusService.markProcessing(outboxEventId);

        if (processingEvent == null) {
            return;
        }

        try {
            OutboxEventHandler handler = findHandler(processingEvent.eventType());

            handler.handle(processingEvent);
            outboxStatusService.markCompleted(processingEvent.id(), processingEvent.processingOwnerId());
        } catch (Exception e) {
            log.error(
                    "Outbox event processing failed. eventId={}, eventType={}",
                    processingEvent.id(),
                    processingEvent.eventType(),
                    e
            );
            try {
                outboxStatusService.markFailed(
                        processingEvent.id(),
                        processingEvent.processingOwnerId(),
                        properties.maxRetryCount(),
                        e.getMessage()
                );
            } catch (IllegalStateException ownerLost) {
                log.warn(
                        "Skipped failed transition because outbox event owner was changed. eventId={}",
                        processingEvent.id(),
                        ownerLost
                );
            }
        }
    }

    private OutboxEventHandler findHandler(String eventType) {
        for (OutboxEventHandler handler : outboxEventHandlers) {
            if (handler.supports(eventType)) {
                return handler;
            }
        }
        throw new IllegalStateException("No handler found for event type: " + eventType);
    }

}
