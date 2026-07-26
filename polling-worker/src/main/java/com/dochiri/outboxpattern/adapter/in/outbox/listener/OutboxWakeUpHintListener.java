package com.dochiri.outboxpattern.adapter.in.outbox.listener;

import com.dochiri.outboxpattern.adapter.in.outbox.worker.OutboxWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "outbox.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxWakeUpHintListener {

    private final OutboxWorker outboxWorker;

    @Async("outboxExecutor")
    @EventListener
    public void onWakeUp(OutboxWakeUpHintEvent event) {
        outboxWorker.runOnce();
    }

}
