package com.dochiri.outboxpattern.infrastructure.outbox.repository;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventEffectPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventEffectPartRepository extends JpaRepository<OutboxEventEffectPart, Long> {

    List<OutboxEventEffectPart> findByOutboxEventIdOrderByPartNumberAsc(Long outboxEventId);
}
