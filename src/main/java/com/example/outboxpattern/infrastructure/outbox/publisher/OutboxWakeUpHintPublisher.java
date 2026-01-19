package com.example.outboxpattern.infrastructure.outbox.publisher;

import com.example.outboxpattern.infrastructure.outbox.listener.OutboxWakeUpHintEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class OutboxWakeUpHintPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            applicationEventPublisher.publishEvent(new OutboxWakeUpHintEvent());
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        applicationEventPublisher.publishEvent(new OutboxWakeUpHintEvent());
                    }
                }
        );
    }

}