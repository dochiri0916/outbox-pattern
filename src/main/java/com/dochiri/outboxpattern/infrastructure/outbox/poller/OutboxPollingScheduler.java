package com.dochiri.outboxpattern.infrastructure.outbox.poller;

import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPollingScheduler {

    private final OutboxWorker outboxWorker;

    // 즉시 트리거(After Commit) 실패 시 안전 장치
    @Scheduled(fixedDelay = 30_000)
    public void poll() {
        outboxWorker.runOnce();
    }

}
