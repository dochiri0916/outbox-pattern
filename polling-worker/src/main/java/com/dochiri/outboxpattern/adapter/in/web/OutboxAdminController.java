package com.dochiri.outboxpattern.adapter.in.web;

import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.adapter.in.outbox.worker.OutboxStatusService;
import com.dochiri.outboxpattern.adapter.out.outbox.repository.OutboxEventAdminProjection;
import com.dochiri.outboxpattern.adapter.out.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureCode;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureType;
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
                .findByEffectiveStatusOrderByCreatedAtAsc(status.name(), PageRequest.of(0, pageSize))
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
            OutboxEventStatus sourceStatus,
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

        private static OutboxEventResponse from(OutboxEventAdminProjection event) {
            return new OutboxEventResponse(
                    event.getId(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getEventType(),
                    OutboxEventStatus.valueOf(event.getEffectiveStatus()),
                    OutboxEventStatus.valueOf(event.getSourceStatus()),
                    event.getAttemptCount(),
                    event.getRetryCount(),
                    event.getFailureType() == null ? null : OutboxFailureType.valueOf(event.getFailureType()),
                    event.getFailureCode() == null ? null : OutboxFailureCode.valueOf(event.getFailureCode()),
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
