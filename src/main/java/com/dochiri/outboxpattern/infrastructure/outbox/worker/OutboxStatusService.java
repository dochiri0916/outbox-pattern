package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxStatusService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public OutboxEventContext markProcessing(Long id) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(id)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        if (event == null || !event.canStartProcessing(now)) {
            return null;
        }

        String processingOwnerId = UUID.randomUUID().toString();
        event.processing(now, processingOwnerId);
        return new OutboxEventContext(event.getId(), event.getEventType(), event.getPayload(), processingOwnerId);
    }

    @Transactional
    public void markCompleted(Long outboxEventId, String processingOwnerId) {
        outboxEventRepository.findByIdForUpdate(outboxEventId).orElseThrow()
                .completed(LocalDateTime.now(), processingOwnerId);
    }

    @Transactional
    public void markFailed(Long outboxEventId, String processingOwnerId, int maxRetryCount, String errorMessage) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(outboxEventId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        event.failed(
                maxRetryCount,
                now,
                nextRetryAt(now, event.getRetryCount() + 1),
                errorMessage,
                processingOwnerId
        );
    }

    @Transactional
    public boolean recoverTimedOutProcessing(Long outboxEventId, LocalDateTime timeoutThreshold, int maxRetryCount) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(outboxEventId).orElse(null);

        if (event == null || !event.isProcessingTimedOut(timeoutThreshold)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        event.recoverTimedOut(
                maxRetryCount,
                now,
                nextRetryAt(now, event.getRetryCount() + 1),
                "PROCESSING timed out",
                timeoutThreshold
        );
        return true;
    }

    @Transactional
    public void retryManually(Long outboxEventId, boolean resetRetryCount) {
        outboxEventRepository.findByIdForUpdate(outboxEventId)
                .orElseThrow()
                .retryManually(resetRetryCount);
    }

    private LocalDateTime nextRetryAt(LocalDateTime now, int retryCountAfterFailure) {
        return now.plus(retryBackoff(retryCountAfterFailure));
    }

    private Duration retryBackoff(int retryCountAfterFailure) {
        return switch (retryCountAfterFailure) {
            case 1 -> Duration.ofSeconds(10);
            case 2 -> Duration.ofSeconds(30);
            case 3 -> Duration.ofMinutes(1);
            default -> Duration.ofMinutes(5);
        };
    }

}
