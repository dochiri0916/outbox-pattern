package com.dochiri.outboxpattern.infrastructure.outbox.listener;

import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxWakeUpHintListener {

    private final OutboxWorker outboxWorker;

    @Async("outboxExecutor")
    @EventListener
    public void onWakeUp(OutboxWakeUpHintEvent event) {
        outboxWorker.runOnce();
    }

}
