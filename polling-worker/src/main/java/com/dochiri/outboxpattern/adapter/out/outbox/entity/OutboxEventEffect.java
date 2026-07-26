package com.dochiri.outboxpattern.adapter.out.outbox.entity;

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
        name = "outbox_event_effects",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbox_event_effect_event_id",
                columnNames = "outbox_event_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_event_id", nullable = false)
    private Long outboxEventId;

    @Column(nullable = false, length = 100)
    private String effectType;

    @Column(length = 512)
    private String multipartUploadId;

    @Column(nullable = false)
    private long processedBytes;

    @Column(nullable = false)
    private LocalDateTime lastProgressAt;

    @Column
    private LocalDateTime completedAt;

    private OutboxEventEffect(
            Long outboxEventId,
            String effectType,
            LocalDateTime lastProgressAt,
            LocalDateTime completedAt
    ) {
        this.outboxEventId = requireNonNull(outboxEventId);
        this.effectType = requireNonNull(effectType);
        this.lastProgressAt = requireNonNull(lastProgressAt);
        this.completedAt = completedAt;
    }

    public static OutboxEventEffect inProgress(Long outboxEventId, String effectType) {
        return new OutboxEventEffect(outboxEventId, effectType, LocalDateTime.now(), null);
    }

    public static OutboxEventEffect completed(Long outboxEventId, String effectType) {
        OutboxEventEffect effect = inProgress(outboxEventId, effectType);
        effect.complete(LocalDateTime.now());
        return effect;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void startMultipartUpload(String uploadId) {
        if (multipartUploadId != null && !multipartUploadId.equals(uploadId)) {
            throw new IllegalStateException("Multipart upload ID cannot be changed");
        }
        this.multipartUploadId = requireNonNull(uploadId);
    }

    public void progress(long processedBytes, LocalDateTime progressAt) {
        if (processedBytes < this.processedBytes) {
            throw new IllegalArgumentException("Processed bytes cannot decrease");
        }
        this.processedBytes = processedBytes;
        this.lastProgressAt = requireNonNull(progressAt);
    }

    public void complete(LocalDateTime completedAt) {
        this.completedAt = requireNonNull(completedAt);
    }
}
