package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimTransactionService {

    private static final int CLAIM_SIZE = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxWorkerProperties outboxWorkerProperties;

    @Transactional
    public OutboxEventContext claimWithSkipLocked() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = outboxEventRepository.findNextPendingBatchWithSkipLocked(
                OutboxEventStatus.PENDING,
                now,
                PageRequest.of(0, CLAIM_SIZE)
        );
        return claim(events, now);
    }

    @Transactional
    public OutboxEventContext claimWithPessimisticWrite() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = outboxEventRepository.findNextPendingBatchWithPessimisticWrite(
                OutboxEventStatus.PENDING,
                now,
                PageRequest.of(0, CLAIM_SIZE)
        );
        return claim(events, now);
    }

    private OutboxEventContext claim(List<OutboxEvent> events, LocalDateTime now) {
        if (events.isEmpty()) {
            return null;
        }

        OutboxEvent event = events.get(0);
        String processingOwnerId = UUID.randomUUID().toString();
        event.processing(
                now,
                processingOwnerId,
                now.plus(outboxWorkerProperties.processingTimeout())
        );
        return new OutboxEventContext(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                processingOwnerId
        );
    }
}
