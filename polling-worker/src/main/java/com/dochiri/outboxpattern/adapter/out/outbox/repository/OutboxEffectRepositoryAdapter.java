package com.dochiri.outboxpattern.adapter.out.outbox.repository;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventEffect;
import com.dochiri.outboxpattern.application.outbox.model.OutboxEffect;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxEffectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEffectRepositoryAdapter implements OutboxEffectRepository {

    private final OutboxEventEffectRepository repository;

    @Override
    public Optional<OutboxEffect> findByOutboxEventId(Long outboxEventId) {
        return repository.findByOutboxEventId(outboxEventId).map(this::toModel);
    }

    @Override
    public OutboxEffect create(OutboxEffect effect) {
        return toModel(repository.save(toEntity(effect)));
    }

    @Override
    public OutboxEffect update(OutboxEffect effect) {
        OutboxEventEffect entity = repository.findByOutboxEventId(effect.outboxEventId())
                .orElseGet(() -> OutboxEventEffect.inProgress(effect.outboxEventId(), effect.effectType()));
        if (effect.multipartUploadId() != null) {
            entity.startMultipartUpload(effect.multipartUploadId());
        }
        if (effect.processedBytes() > 0) {
            entity.progress(effect.processedBytes(), effect.lastProgressAt());
        }
        if (effect.isCompleted() && !entity.isCompleted()) {
            entity.complete(effect.completedAt());
        }
        return toModel(repository.save(entity));
    }

    private OutboxEffect toModel(OutboxEventEffect effect) {
        return OutboxEffect.restore(
                effect.getOutboxEventId(),
                effect.getEffectType(),
                effect.getMultipartUploadId(),
                effect.getProcessedBytes(),
                effect.getLastProgressAt(),
                effect.getCompletedAt()
        );
    }

    private OutboxEventEffect toEntity(OutboxEffect effect) {
        OutboxEventEffect entity = effect.isCompleted()
                ? OutboxEventEffect.completed(effect.outboxEventId(), effect.effectType())
                : OutboxEventEffect.inProgress(effect.outboxEventId(), effect.effectType());
        if (effect.multipartUploadId() != null) {
            entity.startMultipartUpload(effect.multipartUploadId());
        }
        if (effect.processedBytes() > 0) {
            entity.progress(effect.processedBytes(), effect.lastProgressAt());
        }
        if (effect.isCompleted()) {
            entity.complete(effect.completedAt());
        }
        return entity;
    }
}
