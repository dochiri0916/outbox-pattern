package com.dochiri.outboxpattern.infrastructure.outbox.entity;

import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxFailure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_outbox_event_status_retry_created", columnList = "status, next_retry_at, created_at"),
                @Index(name = "idx_outbox_event_status_lease", columnList = "status, lease_until")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private int attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type")
    private OutboxFailureType failureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code")
    private OutboxFailureCode failureCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processing_started_at")
    private LocalDateTime attemptStartedAt;

    private LocalDateTime lastProgressAt;

    private LocalDateTime leaseUntil;

    private String processingOwnerId;

    private LocalDateTime nextRetryAt;

    private LocalDateTime completedAt;

    private LocalDateTime failedAt;

    @Column(length = 1000)
    private String lastErrorMessage;

    @Column(length = 255)
    private String lastExceptionType;

    private LocalDateTime firstFailedAt;

    public static OutboxEvent create(
            String aggregateType, Long aggregateId, String eventType, String payload
    ) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.aggregateType = requireNonNull(aggregateType);
        outboxEvent.aggregateId = requireNonNull(aggregateId);
        outboxEvent.eventType = requireNonNull(eventType);
        outboxEvent.payload = requireNonNull(payload);
        outboxEvent.status = OutboxEventStatus.PENDING;
        outboxEvent.retryCount = 0;
        outboxEvent.attemptCount = 0;
        outboxEvent.createdAt = LocalDateTime.now();
        return outboxEvent;
    }

    public boolean canStartProcessing(LocalDateTime now) {
        return this.status == OutboxEventStatus.PENDING
                && (this.nextRetryAt == null || !this.nextRetryAt.isAfter(now));
    }

    public void processing(LocalDateTime now, String processingOwnerId, LocalDateTime leaseUntil) {
        LocalDateTime startedAt = requireNonNull(now);
        LocalDateTime lease = requireNonNull(leaseUntil);
        if (!canStartProcessing(startedAt)) {
            throw new IllegalStateException("OutboxEvent is not processing");
        }
        if (!lease.isAfter(startedAt)) {
            throw new IllegalArgumentException("Lease must be after processing start time");
        }
        this.status = OutboxEventStatus.PROCESSING;
        this.attemptStartedAt = startedAt;
        this.lastProgressAt = startedAt;
        this.leaseUntil = lease;
        this.processingOwnerId = requireNonNull(processingOwnerId);
        this.attemptCount++;
        this.retryCount = Math.max(0, this.attemptCount - 1);
        this.nextRetryAt = null;
        this.completedAt = null;
        this.failedAt = null;
    }

    public void completed(LocalDateTime now, String processingOwnerId) {
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING events can be marked as completed");
        }
        validateProcessingOwner(processingOwnerId);
        this.status = OutboxEventStatus.COMPLETED;
        this.completedAt = requireNonNull(now);
        this.attemptStartedAt = null;
        this.lastProgressAt = null;
        this.leaseUntil = null;
        this.processingOwnerId = null;
        this.nextRetryAt = null;
    }

    public void failed(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            String errorMessage,
            String processingOwnerId
    ) {
        failed(
                maxRetryCount,
                now,
                nextRetryAt,
                OutboxFailure.retryable(OutboxFailureCode.HANDLER_FAILURE, errorMessage),
                processingOwnerId
        );
    }

    public void failed(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            OutboxFailure failure,
            String processingOwnerId
    ) {
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING events can be marked as failed");
        }
        validateProcessingOwner(processingOwnerId);
        fail(maxRetryCount, now, nextRetryAt, failure);
    }

    public void recoverExpired(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            String errorMessage
    ) {
        recoverExpired(
                maxRetryCount,
                now,
                nextRetryAt,
                OutboxFailure.retryable(OutboxFailureCode.PROCESSING_TIMEOUT, errorMessage),
                null
        );
    }

    public void recoverExpired(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            String errorMessage,
            LocalDateTime legacyTimeoutThreshold
    ) {
        recoverExpired(
                maxRetryCount,
                now,
                nextRetryAt,
                OutboxFailure.retryable(OutboxFailureCode.PROCESSING_TIMEOUT, errorMessage),
                legacyTimeoutThreshold
        );
    }

    public void recoverExpired(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            OutboxFailure failure,
            LocalDateTime legacyTimeoutThreshold
    ) {
        if (!isLeaseExpired(now, legacyTimeoutThreshold)) {
            throw new IllegalStateException("Only lease-expired PROCESSING events can be recovered");
        }
        fail(maxRetryCount, now, nextRetryAt, failure);
    }

    private void fail(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            OutboxFailure failure
    ) {
        OutboxFailure outboxFailure = requireNonNull(failure);
        LocalDateTime failedAt = requireNonNull(now);
        this.failureType = outboxFailure.type();
        this.failureCode = outboxFailure.code();
        this.lastExceptionType = truncate(outboxFailure.exceptionType());
        this.lastErrorMessage = truncate(outboxFailure.message());
        if (this.firstFailedAt == null) {
            this.firstFailedAt = failedAt;
        }
        this.attemptStartedAt = null;
        this.lastProgressAt = null;
        this.leaseUntil = null;
        this.processingOwnerId = null;
        if (outboxFailure.type() == OutboxFailureType.PERMANENT
                || this.retryCount >= Math.max(0, maxRetryCount)) {
            this.status = OutboxEventStatus.FAILED;
            this.failedAt = failedAt;
            this.nextRetryAt = null;
            return;
        }
        this.status = OutboxEventStatus.PENDING;
        this.nextRetryAt = requireNonNull(nextRetryAt);
        this.failedAt = null;
    }

    public boolean isLeaseExpired(LocalDateTime now) {
        return isLeaseExpired(now, null);
    }

    public boolean isLeaseExpired(LocalDateTime now, LocalDateTime legacyTimeoutThreshold) {
        if (this.status != OutboxEventStatus.PROCESSING) {
            return false;
        }
        if (this.leaseUntil != null) {
            return !this.leaseUntil.isAfter(now);
        }
        return legacyTimeoutThreshold != null
                && this.attemptStartedAt != null
                && this.attemptStartedAt.isBefore(legacyTimeoutThreshold);
    }

    public void heartbeat(LocalDateTime now, LocalDateTime nextLeaseUntil, String processingOwnerId) {
        LocalDateTime heartbeatAt = requireNonNull(now);
        LocalDateTime lease = requireNonNull(nextLeaseUntil);
        validateLeaseUpdate(heartbeatAt, lease, processingOwnerId);
        this.leaseUntil = lease;
    }

    public void progress(LocalDateTime now, LocalDateTime nextLeaseUntil, String processingOwnerId) {
        LocalDateTime progressAt = requireNonNull(now);
        LocalDateTime lease = requireNonNull(nextLeaseUntil);
        validateLeaseUpdate(progressAt, lease, processingOwnerId);
        this.lastProgressAt = progressAt;
        this.leaseUntil = lease;
    }

    public boolean isProgressStale(LocalDateTime now, Duration progressTimeout) {
        if (this.lastProgressAt == null) {
            return true;
        }
        return !this.lastProgressAt.plus(progressTimeout).isAfter(now);
    }

    public void retryManually(boolean resetRetryCount) {
        if (this.status != OutboxEventStatus.FAILED) {
            throw new IllegalStateException("Only FAILED events can be retried manually");
        }
        this.status = OutboxEventStatus.PENDING;
        this.attemptStartedAt = null;
        this.lastProgressAt = null;
        this.leaseUntil = null;
        this.processingOwnerId = null;
        this.nextRetryAt = null;
        this.failedAt = null;
        if (resetRetryCount) {
            this.retryCount = 0;
            this.attemptCount = 0;
        }
    }

    private void validateProcessingOwner(String processingOwnerId) {
        if (!requireNonNull(processingOwnerId).equals(this.processingOwnerId)) {
            throw new IllegalStateException("Only current processing owner can change PROCESSING event");
        }
    }

    private void validateLeaseUpdate(
            LocalDateTime now,
            LocalDateTime nextLeaseUntil,
            String processingOwnerId
    ) {
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING events can receive a lease update");
        }
        if (isLeaseExpired(now)) {
            throw new IllegalStateException("Processing lease has expired");
        }
        validateProcessingOwner(processingOwnerId);
        if (!nextLeaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("Lease must be after progress time");
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }

}
