package com.dochiri.outboxpattern.adapter.out.outbox.repository;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventEffectPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventEffectPartRepository extends JpaRepository<OutboxEventEffectPart, Long> {

    List<OutboxEventEffectPart> findByOutboxEventIdOrderByPartNumberAsc(Long outboxEventId);
}
