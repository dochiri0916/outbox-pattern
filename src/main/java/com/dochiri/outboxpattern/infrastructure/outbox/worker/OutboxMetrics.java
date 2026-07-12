package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxFailure;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;
    private final AtomicLong pendingEventCount = new AtomicLong();
    private final AtomicLong oldestPendingEventAgeSeconds = new AtomicLong();
    private final AtomicLong failedEventCount = new AtomicLong();

    public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        this.meterRegistry = meterRegistry;
        this.outboxEventRepository = outboxEventRepository;
        Gauge.builder("outbox.pending.events", pendingEventCount, AtomicLong::doubleValue)
                .description("Number of pending outbox events")
                .register(meterRegistry);
        Gauge.builder("outbox.pending.oldest.age.seconds", oldestPendingEventAgeSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest pending outbox event")
                .register(meterRegistry);
        Gauge.builder("outbox.failed.events", failedEventCount, AtomicLong::doubleValue)
                .description("Number of failed outbox events")
                .register(meterRegistry);
    }

    public Timer.Sample startClaimTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordClaim(Timer.Sample sample, OutboxClaimStrategy strategy, boolean claimed) {
        stopClaimTimer(sample, strategy);
        Counter.builder("outbox.claim.result")
                .tag("strategy", strategy.name().toLowerCase())
                .tag("result", claimed ? "claimed" : "empty")
                .register(meterRegistry)
                .increment();
    }

    public void recordClaimFailure(Timer.Sample sample, OutboxClaimStrategy strategy) {
        stopClaimTimer(sample, strategy);
        Counter.builder("outbox.claim.failure")
                .tag("strategy", strategy.name().toLowerCase())
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordProcessingSuccess(Timer.Sample sample, String eventType) {
        stopProcessingTimer(sample, eventType, "success");
        Counter.builder("outbox.processing.success")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }

    public void recordProcessingFailure(
            Timer.Sample sample,
            String eventType,
            OutboxFailure failure,
            OutboxEventStatus finalStatus
    ) {
        String outcome = outcome(finalStatus);
        stopProcessingTimer(sample, eventType, outcome);
        Counter.builder("outbox.processing.failure")
                .tag("event_type", eventType)
                .tag("failure_type", failure.type().name().toLowerCase())
                .tag("failure_code", failure.code().name().toLowerCase())
                .register(meterRegistry)
                .increment();
        if (finalStatus == OutboxEventStatus.PENDING) {
            Counter.builder("outbox.processing.retry")
                    .tag("event_type", eventType)
                    .register(meterRegistry)
                    .increment();
        }
        if (finalStatus == OutboxEventStatus.FAILED) {
            Counter.builder("outbox.processing.terminal.failure")
                    .tag("event_type", eventType)
                    .tag("failure_code", failure.code().name().toLowerCase())
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordTimeoutRecovery(String eventType) {
        Counter.builder("outbox.processing.timeout.recovery")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }

    public void refreshQueueDepth() {
        pendingEventCount.set(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING));
        failedEventCount.set(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED));
        Optional<LocalDateTime> oldestCreatedAt = outboxEventRepository
                .findFirstByStatusOrderByCreatedAtAscIdAsc(OutboxEventStatus.PENDING)
                .map(event -> event.getCreatedAt());
        oldestPendingEventAgeSeconds.set(oldestCreatedAt
                .map(this::ageSeconds)
                .orElse(0L));
    }

    private void stopClaimTimer(Timer.Sample sample, OutboxClaimStrategy strategy) {
        Timer timer = Timer.builder("outbox.claim.duration")
                .tag("strategy", strategy.name().toLowerCase())
                .register(meterRegistry);
        long elapsedNanos = sample.stop(timer);
        Timer.builder("outbox.claim.lock.wait")
                .tag("strategy", strategy.name().toLowerCase())
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private void stopProcessingTimer(Timer.Sample sample, String eventType, String outcome) {
        sample.stop(Timer.builder("outbox.processing.duration")
                .tag("event_type", eventType)
                .tag("outcome", outcome)
                .register(meterRegistry));
    }

    private String outcome(OutboxEventStatus finalStatus) {
        if (finalStatus == OutboxEventStatus.PENDING) {
            return "retry";
        }
        if (finalStatus == OutboxEventStatus.FAILED) {
            return "failed";
        }
        return "owner_lost";
    }

    private long ageSeconds(LocalDateTime createdAt) {
        return Math.max(0L, Duration.between(createdAt, LocalDateTime.now()).toSeconds());
    }
}
