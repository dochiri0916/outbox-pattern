package com.dochiri.outboxpattern.adapter.in.outbox.worker;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureCode;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailure;
import com.dochiri.outboxpattern.adapter.out.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxProcessingContext;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxProgressPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxStatusService implements OutboxProgressPort {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxWorkerProperties outboxWorkerProperties;
    private final OutboxClaimTransactionService outboxClaimTransactionService;

    public OutboxProcessingContext claimNextPending() {
        if (outboxWorkerProperties.claimStrategy() == OutboxClaimStrategy.PESSIMISTIC_WRITE) {
            return outboxClaimTransactionService.claimWithPessimisticWrite();
        }

        try {
            return outboxClaimTransactionService.claimWithSkipLocked();
        } catch (DataAccessException exception) {
            log.warn("SKIP LOCKED claim failed. Falling back to PESSIMISTIC_WRITE claim", exception);
            return outboxClaimTransactionService.claimWithPessimisticWrite();
        }
    }

    @Transactional
    public OutboxProcessingContext markProcessing(Long id) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(id)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        return startProcessing(event, now);
    }

    @Transactional
    public void markCompleted(Long outboxEventId, String processingOwnerId) {
        outboxEventRepository.findByIdForUpdate(outboxEventId).orElseThrow()
                .completed(LocalDateTime.now(), processingOwnerId);
    }

    @Transactional
    public void markFailed(Long outboxEventId, String processingOwnerId, int maxRetryCount, String errorMessage) {
        markFailed(
                outboxEventId,
                processingOwnerId,
                maxRetryCount,
                OutboxFailure.retryable(OutboxFailureCode.HANDLER_FAILURE, errorMessage)
        );
    }

    @Transactional
    public OutboxEventStatus markFailed(
            Long outboxEventId,
            String processingOwnerId,
            int maxRetryCount,
            OutboxFailure failure
    ) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(outboxEventId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        event.failed(
                maxRetryCount,
                now,
                nextRetryAt(now, event.getRetryCount() + 1),
                failure,
                processingOwnerId
        );
        return event.getStatus();
    }

    @Transactional
    public boolean recoverTimedOutProcessing(Long outboxEventId, LocalDateTime now, int maxRetryCount) {
        return recoverTimedOutProcessing(outboxEventId, now, null, maxRetryCount);
    }

    @Transactional
    public boolean recoverTimedOutProcessing(
            Long outboxEventId,
            LocalDateTime now,
            LocalDateTime legacyTimeoutThreshold,
            int maxRetryCount
    ) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(outboxEventId).orElse(null);

        if (event == null || !event.isLeaseExpired(now, legacyTimeoutThreshold)) {
            return false;
        }

        LocalDateTime recoveryTime = LocalDateTime.now();
        event.recoverExpired(
                maxRetryCount,
                recoveryTime,
                nextRetryAt(recoveryTime, event.getRetryCount() + 1),
                OutboxFailure.retryable(OutboxFailureCode.PROCESSING_TIMEOUT, "PROCESSING timed out"),
                legacyTimeoutThreshold
        );
        return true;
    }

    @Transactional
    public boolean heartbeat(Long outboxEventId, String processingOwnerId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = outboxEventRepository.extendLeaseIfProgressRecent(
                outboxEventId,
                OutboxEventStatus.PROCESSING,
                processingOwnerId,
                now,
                now.plus(outboxWorkerProperties.processingTimeout()),
                now.minus(outboxWorkerProperties.processingTimeout())
        );
        return updated == 1;
    }

    @Transactional
    public void recordProgress(Long outboxEventId, String processingOwnerId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = outboxEventRepository.recordProgress(
                outboxEventId,
                OutboxEventStatus.PROCESSING,
                processingOwnerId,
                now,
                now.plus(outboxWorkerProperties.processingTimeout())
        );
        if (updated != 1) {
            throw new IllegalStateException("Outbox event is no longer owned by processing worker");
        }
    }

    @Transactional
    public void retryManually(Long outboxEventId) {
        retryManually(outboxEventId, false);
    }

    @Transactional
    public void retryManuallyWithReset(Long outboxEventId) {
        retryManually(outboxEventId, true);
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
        long baseDelayMillis = outboxWorkerProperties.retryBackoffBase().toMillis();
        long maxDelayMillis = outboxWorkerProperties.retryBackoffMax().toMillis();
        int exponent = Math.max(0, Math.min(retryCountAfterFailure - 1, 62));
        long multiplier = 1L << exponent;
        long exponentialDelayMillis = baseDelayMillis > maxDelayMillis / multiplier
                ? maxDelayMillis
                : baseDelayMillis * multiplier;
        long jitterRangeMillis = (long) (exponentialDelayMillis * outboxWorkerProperties.retryJitterRatio());
        long minimumDelayMillis = exponentialDelayMillis - jitterRangeMillis;

        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(minimumDelayMillis, exponentialDelayMillis + 1));
    }

    private OutboxProcessingContext startProcessing(OutboxEvent event, LocalDateTime now) {
        if (event == null || !event.canStartProcessing(now)) {
            return null;
        }

        String processingOwnerId = UUID.randomUUID().toString();
        event.processing(now, processingOwnerId, now.plus(outboxWorkerProperties.processingTimeout()));
        return new OutboxProcessingContext(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                processingOwnerId
        );
    }

}
