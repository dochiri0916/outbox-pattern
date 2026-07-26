package com.dochiri.outboxpattern.application.outbox.port.out;

import com.dochiri.outboxpattern.application.outbox.model.OutboxEffectPart;
import java.util.List;

public interface OutboxEffectPartRepository {

    List<OutboxEffectPart> findByOutboxEventId(Long outboxEventId);

    OutboxEffectPart create(OutboxEffectPart part);
}
