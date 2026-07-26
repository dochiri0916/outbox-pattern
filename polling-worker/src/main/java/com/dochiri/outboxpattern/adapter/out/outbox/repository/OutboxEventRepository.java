package com.dochiri.outboxpattern.adapter.out.outbox.repository;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

    @Modifying
    @Query("""
            update OutboxEvent e
               set e.leaseUntil = :leaseUntil
             where e.id = :id
               and e.status = :status
               and e.processingOwnerId = :processingOwnerId
               and e.leaseUntil > :now
               and e.lastProgressAt is not null
               and e.lastProgressAt > :progressThreshold
            """)
    int extendLeaseIfProgressRecent(
            @Param("id") Long id,
            @Param("status") OutboxEventStatus status,
            @Param("processingOwnerId") String processingOwnerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("progressThreshold") LocalDateTime progressThreshold
    );

    @Modifying
    @Query("""
            update OutboxEvent e
               set e.lastProgressAt = :now,
                   e.leaseUntil = :leaseUntil
             where e.id = :id
               and e.status = :status
               and e.processingOwnerId = :processingOwnerId
               and e.leaseUntil > :now
            """)
    int recordProgress(
            @Param("id") Long id,
            @Param("status") OutboxEventStatus status,
            @Param("processingOwnerId") String processingOwnerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc, e.id asc
            """)
    List<OutboxEvent> findNextPendingBatchWithSkipLocked(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc, e.id asc
            """)
    List<OutboxEvent> findNextPendingBatchWithPessimisticWrite(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and (e.nextRetryAt is null or e.nextRetryAt <= :now)
            order by e.createdAt asc, e.id asc
            """)
    List<OutboxEvent> findNextBatch(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select e from OutboxEvent e
            where e.status = :status
              and (
                    (e.leaseUntil is not null and e.leaseUntil <= :now)
                    or (e.leaseUntil is null and e.attemptStartedAt < :legacyTimeoutThreshold)
            )
            order by e.leaseUntil asc, e.attemptStartedAt asc, e.id asc
            """)
    List<OutboxEvent> findLeaseExpiredProcessingBatch(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            @Param("legacyTimeoutThreshold") LocalDateTime legacyTimeoutThreshold,
            Pageable pageable
    );

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    @Query(value = """
            select e.id as id,
                   e.aggregate_type as aggregateType,
                   e.aggregate_id as aggregateId,
                   e.event_type as eventType,
                   e.status as sourceStatus,
                   case
                       when e.status = 'FAILED' then 'FAILED'
                       when effect.completed_at is not null then 'COMPLETED'
                       when effect.outbox_event_id is not null then 'PROCESSING'
                       else e.status
                   end as effectiveStatus,
                   e.attempt_count as attemptCount,
                   e.retry_count as retryCount,
                   e.failure_type as failureType,
                   e.failure_code as failureCode,
                   e.last_exception_type as lastExceptionType,
                   e.first_failed_at as firstFailedAt,
                   e.processing_started_at as attemptStartedAt,
                   e.last_progress_at as lastProgressAt,
                   e.lease_until as leaseUntil,
                   e.next_retry_at as nextRetryAt,
                   e.created_at as createdAt,
                   coalesce(effect.completed_at, e.completed_at) as completedAt,
                   e.failed_at as failedAt,
                   e.last_error_message as lastErrorMessage
              from outbox_event e
              left join outbox_event_effects effect
                on effect.outbox_event_id = e.id
             where case
                       when e.status = 'FAILED' then 'FAILED'
                       when effect.completed_at is not null then 'COMPLETED'
                       when effect.outbox_event_id is not null then 'PROCESSING'
                       else e.status
                   end = :status
             order by e.created_at asc, e.id asc
            """, nativeQuery = true)
    List<OutboxEventAdminProjection> findByEffectiveStatusOrderByCreatedAtAsc(
            @Param("status") String status,
            Pageable pageable
    );

    long countByStatus(OutboxEventStatus status);

    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAscIdAsc(OutboxEventStatus status);

}
