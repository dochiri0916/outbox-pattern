package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.worker")
public record OutboxWorkerProperties(
        int batchSize,
        int maxRetryCount,
        Duration processingTimeout,
        Duration pollingDelay,
        Duration recoveryDelay
) {

    public OutboxWorkerProperties {
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 5;
        }
        if (processingTimeout == null) {
            processingTimeout = Duration.ofMinutes(5);
        }
        if (pollingDelay == null) {
            pollingDelay = Duration.ofSeconds(30);
        }
        if (recoveryDelay == null) {
            recoveryDelay = Duration.ofSeconds(30);
        }
    }

}
