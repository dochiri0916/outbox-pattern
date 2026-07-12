package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.worker")
public record OutboxWorkerProperties(
        int batchSize,
        int maxRetryCount,
        Duration processingTimeout,
        Duration pollingDelay,
        Duration recoveryDelay,
        Duration retryBackoffBase,
        Duration retryBackoffMax,
        double retryJitterRatio,
        long multipartPartSizeBytes,
        OutboxClaimStrategy claimStrategy
) {
    public OutboxWorkerProperties {
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 3;
        }
        if (processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()) {
            processingTimeout = Duration.ofMinutes(5);
        }
        if (pollingDelay == null) {
            pollingDelay = Duration.ofSeconds(30);
        }
        if (recoveryDelay == null) {
            recoveryDelay = Duration.ofSeconds(30);
        }
        if (retryBackoffBase == null || retryBackoffBase.isZero() || retryBackoffBase.isNegative()) {
            retryBackoffBase = Duration.ofSeconds(10);
        }
        if (retryBackoffMax == null || retryBackoffMax.isZero() || retryBackoffMax.isNegative()) {
            retryBackoffMax = Duration.ofMinutes(5);
        }
        if (retryBackoffMax.compareTo(retryBackoffBase) < 0) {
            retryBackoffMax = retryBackoffBase;
        }
        if (retryJitterRatio < 0 || retryJitterRatio > 1 || !Double.isFinite(retryJitterRatio)) {
            retryJitterRatio = 0.5;
        }
        if (multipartPartSizeBytes <= 0) {
            multipartPartSizeBytes = 5 * 1024 * 1024L;
        }
        if (claimStrategy == null) {
            claimStrategy = OutboxClaimStrategy.SKIP_LOCKED;
        }
    }

    public Duration leaseHeartbeatInterval() {
        long intervalMillis = Math.max(1, processingTimeout.toMillis() / 2);
        return Duration.ofMillis(intervalMillis);
    }

}
