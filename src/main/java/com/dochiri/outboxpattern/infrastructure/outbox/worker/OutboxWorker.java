package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureCode;
import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxFailure;
import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxFailureClassifier;
import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxProcessingException;
import com.dochiri.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PreDestroy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxStatusService outboxStatusService;
    private final List<OutboxEventHandler> outboxEventHandlers;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxWorkerProperties outboxWorkerProperties;
    private final OutboxFailureClassifier outboxFailureClassifier;
    private final OutboxMetrics outboxMetrics;
    private final ScheduledExecutorService leaseHeartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "outbox-lease-heartbeat");
                thread.setDaemon(true);
                return thread;
            }
    );

    public void runOnce() {
        try {
            for (int i = 0; i < outboxWorkerProperties.batchSize(); i++) {
                Timer.Sample claimTimer = outboxMetrics.startClaimTimer();
                OutboxEventContext processingEvent;
                try {
                    processingEvent = outboxStatusService.claimNextPending();
                    outboxMetrics.recordClaim(
                            claimTimer,
                            outboxWorkerProperties.claimStrategy(),
                            processingEvent != null
                    );
                } catch (RuntimeException exception) {
                    outboxMetrics.recordClaimFailure(claimTimer, outboxWorkerProperties.claimStrategy());
                    log.error("Outbox event claim failed", exception);
                    break;
                }
                if (processingEvent == null) {
                    break;
                }
                process(processingEvent);
            }
        } finally {
            refreshQueueMetrics();
        }
    }

    public void recoverTimedOutProcessingEvents() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime legacyTimeoutThreshold = now.minus(outboxWorkerProperties.processingTimeout());
        List<OutboxEvent> timedOutEvents = outboxEventRepository.findLeaseExpiredProcessingBatch(
                OutboxEventStatus.PROCESSING,
                now,
                legacyTimeoutThreshold,
                PageRequest.of(0, outboxWorkerProperties.batchSize())
        );

        for (OutboxEvent timedOutEvent : timedOutEvents) {
            boolean recovered = outboxStatusService.recoverTimedOutProcessing(
                    timedOutEvent.getId(),
                    now,
                    legacyTimeoutThreshold,
                    outboxWorkerProperties.maxRetryCount()
            );

            if (recovered) {
                outboxMetrics.recordTimeoutRecovery(timedOutEvent.getEventType());
                log.warn(
                        "Recovered expired PROCESSING outbox event. eventId={}, aggregateId={}, eventType={}, processingOwnerId={}",
                        timedOutEvent.getId(),
                        timedOutEvent.getAggregateId(),
                        timedOutEvent.getEventType(),
                        timedOutEvent.getProcessingOwnerId()
                );
            }
        }
        refreshQueueMetrics();
    }

    @PreDestroy
    void shutdownLeaseHeartbeatExecutor() {
        leaseHeartbeatExecutor.shutdownNow();
    }

    private void process(OutboxEventContext processingEvent) {
        ScheduledFuture<?> heartbeat = startLeaseHeartbeat(processingEvent);
        Timer.Sample processingTimer = outboxMetrics.startProcessingTimer();
        try {
            OutboxEventHandler handler = findHandler(processingEvent.eventType());

            handler.handle(processingEvent);
            outboxStatusService.markCompleted(processingEvent.id(), processingEvent.processingOwnerId());
            outboxMetrics.recordProcessingSuccess(processingTimer, processingEvent.eventType());
            log.info(
                    "Outbox event processing completed. eventId={}, aggregateId={}, eventType={}, processingOwnerId={}",
                    processingEvent.id(),
                    processingEvent.aggregateId(),
                    processingEvent.eventType(),
                    processingEvent.processingOwnerId()
            );
        } catch (Exception e) {
            OutboxFailure failure = outboxFailureClassifier.classify(e);
            OutboxEventStatus finalStatus = null;
            log.error(
                    "Outbox event processing failed. eventId={}, aggregateId={}, eventType={}, processingOwnerId={}, failureType={}, failureCode={}",
                    processingEvent.id(),
                    processingEvent.aggregateId(),
                    processingEvent.eventType(),
                    processingEvent.processingOwnerId(),
                    failure.type(),
                    failure.code(),
                    e
            );
            try {
                finalStatus = outboxStatusService.markFailed(
                        processingEvent.id(),
                        processingEvent.processingOwnerId(),
                        outboxWorkerProperties.maxRetryCount(),
                        failure
                );
            } catch (IllegalStateException ownerLost) {
                log.warn(
                        "Skipped failed transition because outbox event owner was changed. eventId={}, aggregateId={}, processingOwnerId={}",
                        processingEvent.id(),
                        processingEvent.aggregateId(),
                        processingEvent.processingOwnerId(),
                        ownerLost
                );
            } catch (RuntimeException statusUpdateFailure) {
                log.error(
                        "Failed to persist outbox failure state. eventId={}, aggregateId={}, processingOwnerId={}",
                        processingEvent.id(),
                        processingEvent.aggregateId(),
                        processingEvent.processingOwnerId(),
                        statusUpdateFailure
                );
            } finally {
                outboxMetrics.recordProcessingFailure(
                        processingTimer,
                        processingEvent.eventType(),
                        failure,
                        finalStatus
                );
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    private ScheduledFuture<?> startLeaseHeartbeat(OutboxEventContext processingEvent) {
        long intervalMillis = outboxWorkerProperties.leaseHeartbeatInterval().toMillis();
        return leaseHeartbeatExecutor.scheduleAtFixedRate(
                () -> sendLeaseHeartbeat(processingEvent),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private void sendLeaseHeartbeat(OutboxEventContext processingEvent) {
        try {
            outboxStatusService.heartbeat(processingEvent.id(), processingEvent.processingOwnerId());
        } catch (RuntimeException e) {
            log.warn(
                    "Outbox event lease heartbeat failed. eventId={}, processingOwnerId={}",
                    processingEvent.id(),
                    processingEvent.processingOwnerId(),
                    e
            );
        }
    }

    private OutboxEventHandler findHandler(String eventType) {
        for (OutboxEventHandler handler : outboxEventHandlers) {
            if (handler.supports(eventType)) {
                return handler;
            }
        }
        throw OutboxProcessingException.permanent(
                OutboxFailureCode.UNSUPPORTED_EVENT_TYPE,
                new IllegalStateException("No handler found for event type: " + eventType)
        );
    }

    private void refreshQueueMetrics() {
        try {
            outboxMetrics.refreshQueueDepth();
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh outbox queue metrics", exception);
        }
    }

}
