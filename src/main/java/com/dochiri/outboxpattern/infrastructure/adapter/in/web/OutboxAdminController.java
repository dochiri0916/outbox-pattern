package com.dochiri.outboxpattern.infrastructure.adapter.in.web;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureCode;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureType;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/outbox-events")
@RequiredArgsConstructor
public class OutboxAdminController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxStatusService outboxStatusService;

    @GetMapping
    public ResponseEntity<List<OutboxEventResponse>> list(
            @RequestParam(defaultValue = "FAILED") OutboxEventStatus status,
            @RequestParam(defaultValue = "50") int size
    ) {
        int pageSize = Math.min(Math.max(size, 1), DEFAULT_PAGE_SIZE);
        List<OutboxEventResponse> events = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, pageSize))
                .stream()
                .map(OutboxEventResponse::from)
                .toList();

        return ResponseEntity.ok(events);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(
            @PathVariable Long id
    ) {
        outboxStatusService.retryManually(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry/reset")
    public ResponseEntity<Void> retryWithReset(@PathVariable Long id) {
        outboxStatusService.retryManuallyWithReset(id);
        return ResponseEntity.noContent().build();
    }

    public record OutboxEventResponse(
            Long id,
            String aggregateType,
            Long aggregateId,
            String eventType,
            OutboxEventStatus status,
            int attemptCount,
            int retryCount,
            OutboxFailureType failureType,
            OutboxFailureCode failureCode,
            String lastExceptionType,
            LocalDateTime firstFailedAt,
            LocalDateTime attemptStartedAt,
            LocalDateTime lastProgressAt,
            LocalDateTime leaseUntil,
            LocalDateTime nextRetryAt,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            LocalDateTime failedAt,
            String lastErrorMessage
    ) {

        private static OutboxEventResponse from(OutboxEvent event) {
            return new OutboxEventResponse(
                    event.getId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getEventType(),
                    event.getStatus(),
                    event.getAttemptCount(),
                    event.getRetryCount(),
                    event.getFailureType(),
                    event.getFailureCode(),
                    event.getLastExceptionType(),
                    event.getFirstFailedAt(),
                    event.getAttemptStartedAt(),
                    event.getLastProgressAt(),
                    event.getLeaseUntil(),
                    event.getNextRetryAt(),
                    event.getCreatedAt(),
                    event.getCompletedAt(),
                    event.getFailedAt(),
                    event.getLastErrorMessage()
            );
        }
    }

}
