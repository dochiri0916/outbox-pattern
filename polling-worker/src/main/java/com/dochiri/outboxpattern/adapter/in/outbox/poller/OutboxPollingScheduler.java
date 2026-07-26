package com.dochiri.outboxpattern.adapter.in.outbox.poller;

import com.dochiri.outboxpattern.adapter.in.outbox.worker.OutboxWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "outbox.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxPollingScheduler {

    private final OutboxWorker outboxWorker;

    // 즉시 트리거(After Commit) 실패 시 안전 장치
    @Scheduled(fixedDelayString = "${outbox.worker.polling-delay:30s}")
    public void poll() {
        outboxWorker.runOnce();
    }

    @Scheduled(fixedDelayString = "${outbox.worker.recovery-delay:30s}")
    public void recoverTimedOutProcessingEvents() {
        outboxWorker.recoverTimedOutProcessingEvents();
    }

}
