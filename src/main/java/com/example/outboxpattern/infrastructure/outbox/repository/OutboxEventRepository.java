package com.example.outboxpattern.infrastructure.outbox.repository;

import com.example.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.example.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            order by e.createdAt asc
            """)
    List<OutboxEvent> findNextBatch(
            @Param("status") OutboxEventStatus status,
            Pageable pageable
    );

}