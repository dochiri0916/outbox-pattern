package com.dochiri.outboxpattern.infrastructure.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

@Entity
@Table(
        name = "outbox_event_effect_parts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbox_event_effect_part",
                columnNames = {"outbox_event_id", "part_number"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEffectPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_event_id", nullable = false)
    private Long outboxEventId;

    @Column(name = "part_number", nullable = false)
    private int partNumber;

    @Column(nullable = false, length = 512)
    private String eTag;

    @Column(nullable = false)
    private long contentLength;

    @Column(nullable = false, updatable = false)
    private LocalDateTime completedAt;

    private OutboxEventEffectPart(
            Long outboxEventId,
            int partNumber,
            String eTag,
            long contentLength,
            LocalDateTime completedAt
    ) {
        this.outboxEventId = requireNonNull(outboxEventId);
        this.partNumber = partNumber;
        this.eTag = requireNonNull(eTag);
        this.contentLength = contentLength;
        this.completedAt = requireNonNull(completedAt);
    }

    public static OutboxEventEffectPart completed(
            Long outboxEventId,
            int partNumber,
            String eTag,
            long contentLength
    ) {
        return new OutboxEventEffectPart(
                outboxEventId,
                partNumber,
                eTag,
                contentLength,
                LocalDateTime.now()
        );
    }
}
