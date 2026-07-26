package com.dochiri.outboxpattern.adapter.out.outbox.repository;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventEffect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutboxEventEffectRepository extends JpaRepository<OutboxEventEffect, Long> {

    Optional<OutboxEventEffect> findByOutboxEventId(Long outboxEventId);

    boolean existsByOutboxEventId(Long outboxEventId);
}
