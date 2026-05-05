package com.dochiri.outboxpattern.infrastructure.outbox.recorder;

import com.dochiri.outboxpattern.application.event.ApplicationEvent;
import com.dochiri.outboxpattern.application.event.port.out.EventPublisher;
import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.publisher.OutboxWakeUpHintPublisher;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisherAdapter implements EventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadSerializer outboxPayloadSerializer;
    private final OutboxWakeUpHintPublisher outboxWakeUpHintPublisher;

    @Transactional
    @Override
    public void publish(ApplicationEvent event) {
        OutboxRecord record = toOutboxRecord(event);
        String serializedPayload = outboxPayloadSerializer.serialize(event);

        outboxEventRepository.save(OutboxEvent.create(
                record.aggregateType(),
                record.aggregateId(),
                record.eventType(),
                serializedPayload
        ));

        outboxWakeUpHintPublisher.publishAfterCommit();
    }

    private OutboxRecord toOutboxRecord(ApplicationEvent event) {
        if (event instanceof PostFileUploadRequestedEvent postFileUploadRequestedEvent) {
            return new OutboxRecord(
                    OutboxEventNames.POST,
                    postFileUploadRequestedEvent.postId(),
                    OutboxEventNames.POST_FILE_UPLOAD
            );
        }

        throw new IllegalArgumentException("Unsupported application event: " + event.getClass().getName());
    }

    private record OutboxRecord(String aggregateType, Long aggregateId, String eventType) {
    }

}
