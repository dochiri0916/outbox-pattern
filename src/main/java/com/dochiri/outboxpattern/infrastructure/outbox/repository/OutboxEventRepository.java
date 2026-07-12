package com.dochiri.outboxpattern.infrastructure.outbox.repository;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
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

    long countByStatus(OutboxEventStatus status);

    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAscIdAsc(OutboxEventStatus status);

}
