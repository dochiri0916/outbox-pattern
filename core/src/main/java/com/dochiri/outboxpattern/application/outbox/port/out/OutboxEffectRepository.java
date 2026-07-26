package com.dochiri.outboxpattern.application.outbox.port.out;

import com.dochiri.outboxpattern.application.outbox.model.OutboxEffect;
import java.util.Optional;

public interface OutboxEffectRepository {

    Optional<OutboxEffect> findByOutboxEventId(Long outboxEventId);

    OutboxEffect create(OutboxEffect effect);

    OutboxEffect update(OutboxEffect effect);
}
