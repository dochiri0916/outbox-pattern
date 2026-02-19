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
                @Index(name = "idx_outbox_event_status_created", columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AggregateType aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventType eventType;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static OutboxEvent create(
            AggregateType aggregateType, Long aggregateId, OutboxEventType eventType, String payload
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

    public boolean canStartProcessing() {
        return this.status == OutboxEventStatus.PENDING;
    }

    public void processing() {
        if (this.status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("OutboxEvent is not processing");
        }
        this.status = OutboxEventStatus.PROCESSING;
    }

    public void completed() {
        this.status = OutboxEventStatus.COMPLETED;
    }

    public void failed(int maxRetryCount) {
        this.retryCount++;
        if (this.retryCount >= maxRetryCount) {
            this.status = OutboxEventStatus.FAILED;
            return;
        }
        this.status = OutboxEventStatus.PENDING;
    }

}