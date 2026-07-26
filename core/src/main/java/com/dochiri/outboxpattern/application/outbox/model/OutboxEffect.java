package com.dochiri.outboxpattern.application.outbox.model;

import java.time.LocalDateTime;

public final class OutboxEffect {

    private final Long outboxEventId;
    private final String effectType;
    private String multipartUploadId;
    private long processedBytes;
    private LocalDateTime lastProgressAt;
    private LocalDateTime completedAt;

    private OutboxEffect(
            Long outboxEventId,
            String effectType,
            String multipartUploadId,
            long processedBytes,
            LocalDateTime lastProgressAt,
            LocalDateTime completedAt
    ) {
        this.outboxEventId = outboxEventId;
        this.effectType = effectType;
        this.multipartUploadId = multipartUploadId;
        this.processedBytes = processedBytes;
        this.lastProgressAt = lastProgressAt;
        this.completedAt = completedAt;
    }

    public static OutboxEffect inProgress(Long outboxEventId, String effectType) {
        return new OutboxEffect(outboxEventId, effectType, null, 0, null, null);
    }

    public static OutboxEffect restore(
            Long outboxEventId,
            String effectType,
            String multipartUploadId,
            long processedBytes,
            LocalDateTime lastProgressAt,
            LocalDateTime completedAt
    ) {
        return new OutboxEffect(
                outboxEventId,
                effectType,
                multipartUploadId,
                processedBytes,
                lastProgressAt,
                completedAt
        );
    }

    public Long outboxEventId() { return outboxEventId; }
    public String effectType() { return effectType; }
    public String multipartUploadId() { return multipartUploadId; }
    public long processedBytes() { return processedBytes; }
    public LocalDateTime lastProgressAt() { return lastProgressAt; }
    public LocalDateTime completedAt() { return completedAt; }

    public boolean isCompleted() { return completedAt != null; }

    public void startMultipartUpload(String uploadId) {
        this.multipartUploadId = uploadId;
    }

    public void progress(long processedBytes, LocalDateTime progressAt) {
        this.processedBytes = processedBytes;
        this.lastProgressAt = progressAt;
    }

    public void complete(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
