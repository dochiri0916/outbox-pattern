package com.dochiri.outboxpattern.infrastructure.outbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static java.util.Objects.*;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_outbox_event_status_retry_created", columnList = "status, next_retry_at, created_at"),
                @Index(name = "idx_outbox_event_status_processing_started", columnList = "status, processing_started_at")
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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processingStartedAt;

    private String processingOwnerId;

    private LocalDateTime nextRetryAt;

    private LocalDateTime completedAt;

    private LocalDateTime failedAt;

    @Column(length = 1000)
    private String lastErrorMessage;

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
        outboxEvent.createdAt = LocalDateTime.now();
        return outboxEvent;
    }

    public boolean canStartProcessing(LocalDateTime now) {
        return this.status == OutboxEventStatus.PENDING
                && (this.nextRetryAt == null || !this.nextRetryAt.isAfter(now));
    }

    public void processing(LocalDateTime now, String processingOwnerId) {
        if (!canStartProcessing(now)) {
            throw new IllegalStateException("OutboxEvent is not processing");
        }
        this.status = OutboxEventStatus.PROCESSING;
        this.processingStartedAt = requireNonNull(now);
        this.processingOwnerId = requireNonNull(processingOwnerId);
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
        this.processingStartedAt = null;
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
        if (this.status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING events can be marked as failed");
        }
        validateProcessingOwner(processingOwnerId);
        fail(maxRetryCount, now, nextRetryAt, errorMessage);
    }

    public void recoverTimedOut(
            int maxRetryCount,
            LocalDateTime now,
            LocalDateTime nextRetryAt,
            String errorMessage,
            LocalDateTime timeoutThreshold
    ) {
        if (!isProcessingTimedOut(timeoutThreshold)) {
            throw new IllegalStateException("Only timed out PROCESSING events can be recovered");
        }
        fail(maxRetryCount, now, nextRetryAt, errorMessage);
    }

    private void fail(int maxRetryCount, LocalDateTime now, LocalDateTime nextRetryAt, String errorMessage) {
        this.retryCount++;
        this.lastErrorMessage = truncate(errorMessage);
        this.processingStartedAt = null;
        this.processingOwnerId = null;
        if (this.retryCount >= maxRetryCount) {
            this.status = OutboxEventStatus.FAILED;
            this.failedAt = requireNonNull(now);
            this.nextRetryAt = null;
            return;
        }
        this.status = OutboxEventStatus.PENDING;
        this.nextRetryAt = requireNonNull(nextRetryAt);
    }

    public boolean isProcessingTimedOut(LocalDateTime timeoutThreshold) {
        return this.status == OutboxEventStatus.PROCESSING
                && this.processingStartedAt != null
                && this.processingStartedAt.isBefore(timeoutThreshold);
    }

    public void retryManually(boolean resetRetryCount) {
        if (this.status != OutboxEventStatus.FAILED) {
            throw new IllegalStateException("Only FAILED events can be retried manually");
        }
        this.status = OutboxEventStatus.PENDING;
        this.processingStartedAt = null;
        this.processingOwnerId = null;
        this.nextRetryAt = null;
        this.failedAt = null;
        if (resetRetryCount) {
            this.retryCount = 0;
        }
    }

    private void validateProcessingOwner(String processingOwnerId) {
        if (!requireNonNull(processingOwnerId).equals(this.processingOwnerId)) {
            throw new IllegalStateException("Only current processing owner can change PROCESSING event");
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }

}
