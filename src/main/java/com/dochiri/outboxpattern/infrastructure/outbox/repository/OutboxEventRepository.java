package com.dochiri.outboxpattern.infrastructure.outbox.repository;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
                select e from OutboxEvent e
                where e.id = :id
            """)
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc
            """)
    List<OutboxEvent> findNextBatch(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and e.processingStartedAt < :timeoutThreshold
            order by e.processingStartedAt asc
            """)
    List<OutboxEvent> findTimedOutProcessingBatch(
            @Param("status") OutboxEventStatus status,
            @Param("timeoutThreshold") LocalDateTime timeoutThreshold,
            Pageable pageable
    );

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

}
