package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxStatusService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public OutboxEvent markProcessing(Long id) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(id)
                .orElse(null);

        if (event == null || !event.canStartProcessing()) {
            return null;
        }

        event.processing();
        return event;
    }

    @Transactional
    public void markCompleted(Long outboxEventId) {
        outboxEventRepository.findByIdForUpdate(outboxEventId).orElseThrow().completed();
    }

    @Transactional
    public void markFailed(Long outboxEventId, int maxRetryCount) {
        outboxEventRepository.findByIdForUpdate(outboxEventId).orElseThrow().failed(maxRetryCount);
    }

}