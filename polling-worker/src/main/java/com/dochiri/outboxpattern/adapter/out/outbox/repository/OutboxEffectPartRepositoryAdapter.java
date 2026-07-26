package com.dochiri.outboxpattern.adapter.out.outbox.repository;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventEffectPart;
import com.dochiri.outboxpattern.application.outbox.model.OutboxEffectPart;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxEffectPartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEffectPartRepositoryAdapter implements OutboxEffectPartRepository {

    private final OutboxEventEffectPartRepository repository;

    @Override
    public List<OutboxEffectPart> findByOutboxEventId(Long outboxEventId) {
        return repository.findByOutboxEventIdOrderByPartNumberAsc(outboxEventId).stream()
                .map(part -> new OutboxEffectPart(
                        part.getOutboxEventId(),
                        part.getPartNumber(),
                        part.getETag(),
                        part.getContentLength()
                ))
                .toList();
    }

    @Override
    public OutboxEffectPart create(OutboxEffectPart part) {
        repository.save(OutboxEventEffectPart.completed(
                part.outboxEventId(),
                part.partNumber(),
                part.eTag(),
                part.contentLength()
        ));
        return part;
    }
}
