package com.dochiri.outboxpattern.infrastructure.outbox.recorder;

import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.AggregateType;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;
import com.dochiri.outboxpattern.infrastructure.outbox.publisher.OutboxWakeUpHintPublisher;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadSerializer outboxPayloadSerializer;
    private final OutboxWakeUpHintPublisher outboxWakeUpHintPublisher;

    @Transactional
    public void record(AggregateType aggregateType, Long aggregateId, OutboxEventType outboxEventType, Object payload) {
        String serializedPayload = outboxPayloadSerializer.serialize(payload);

        outboxEventRepository.save(OutboxEvent.create(
                aggregateType,
                aggregateId,
                outboxEventType,
                serializedPayload
        ));

        outboxWakeUpHintPublisher.publishAfterCommit();
    }

}