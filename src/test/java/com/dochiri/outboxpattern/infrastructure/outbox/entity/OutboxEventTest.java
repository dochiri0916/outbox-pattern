package com.dochiri.outboxpattern.infrastructure.outbox.entity;

import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxEventTest {

    private static final String OWNER_ID = "owner-1";

    @Test
    @DisplayName("PENDING 이벤트를 처리 후 COMPLETED로 전이한다")
    void should_transition_from_pending_to_processing_to_completed() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        assertTrue(event.canStartProcessing(now));

        event.processing(now, OWNER_ID, now.plusMinutes(5));
        event.completed(now.plusSeconds(1), OWNER_ID);

        assertEquals(OutboxEventStatus.COMPLETED, event.getStatus());
        assertNull(event.getAttemptStartedAt());
        assertNull(event.getProcessingOwnerId());
        assertNull(event.getNextRetryAt());
        assertNotNull(event.getCompletedAt());
    }

    @Test
    @DisplayName("허용된 재시도 횟수 전에는 PENDING으로 되돌린다")
    void should_return_to_pending_when_failed_before_max_retry() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryAt = now.plusSeconds(10);
        event.processing(now, OWNER_ID, now.plusMinutes(5));

        event.failed(5, now, nextRetryAt, "failed", OWNER_ID);

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertEquals(0, event.getRetryCount());
        assertNull(event.getAttemptStartedAt());
        assertNull(event.getProcessingOwnerId());
        assertEquals(nextRetryAt, event.getNextRetryAt());
        assertEquals("failed", event.getLastErrorMessage());
    }

    @Test
    @DisplayName("최초 시도와 허용된 재시도 후 terminal failure로 전이한다")
    void should_be_failed_after_initial_attempt_and_allowed_retries() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 4; i++) {
            LocalDateTime processingAt = now.plusMinutes(i * 2L);
            event.processing(processingAt, OWNER_ID, processingAt.plusMinutes(1));
            event.failed(3, processingAt.plusSeconds(1), processingAt.plusMinutes(1), "failed", OWNER_ID);
        }

        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(4, event.getAttemptCount());
        assertEquals(3, event.getRetryCount());
        assertNull(event.getAttemptStartedAt());
        assertNull(event.getProcessingOwnerId());
        assertNull(event.getNextRetryAt());
        assertNotNull(event.getFailedAt());
    }

    @Test
    @DisplayName("처리 중이 아닌 이벤트를 실패 처리하면 예외가 발생한다")
    void should_throw_when_failed_called_outside_processing() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> event.failed(5, now, now.plusSeconds(10), "failed", OWNER_ID)
        );

        assertEquals("Only PROCESSING events can be marked as failed", exception.getMessage());
    }

    @Test
    @DisplayName("처리 중이 아닌 이벤트를 완료 처리하면 예외가 발생한다")
    void should_throw_when_completed_called_outside_processing() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> event.completed(now, OWNER_ID)
        );

        assertEquals("Only PROCESSING events can be marked as completed", exception.getMessage());
    }

    @Test
    @DisplayName("다른 processing owner는 이벤트를 완료 처리할 수 없다")
    void should_throw_when_completed_by_different_processing_owner() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        event.processing(now, OWNER_ID, now.plusMinutes(5));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> event.completed(now.plusSeconds(1), "owner-2")
        );

        assertEquals("Only current processing owner can change PROCESSING event", exception.getMessage());
    }

    @Test
    @DisplayName("오래된 owner는 재처리된 이벤트를 완료 처리할 수 없다")
    void should_prevent_stale_owner_from_completing_reprocessed_event() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        event.processing(now.minusMinutes(10), OWNER_ID, now.minusMinutes(6));
        event.recoverExpired(5, now, now.plusSeconds(10), "PROCESSING timed out");
        event.processing(now.plusSeconds(11), "owner-2", now.plusMinutes(5));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> event.completed(now.plusSeconds(12), OWNER_ID)
        );

        assertEquals("Only current processing owner can change PROCESSING event", exception.getMessage());
    }

    @Test
    @DisplayName("lease가 만료된 processing 이벤트를 감지한다")
    void should_detect_timed_out_processing_event() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime attemptStartedAt = LocalDateTime.now().minusMinutes(10);

        event.processing(attemptStartedAt, OWNER_ID, attemptStartedAt.plusMinutes(5));

        assertTrue(event.isLeaseExpired(LocalDateTime.now()));
    }

    @Test
    @DisplayName("최초 시도와 실제 재시도 횟수를 분리해 기록한다")
    void should_separate_initial_attempt_from_retry_count() {
        // given
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        // when
        event.processing(now, OWNER_ID, now.plusMinutes(5));
        event.failed(3, now.plusSeconds(1), now.plusSeconds(10), "failed", OWNER_ID);
        event.processing(now.plusSeconds(11), "owner-2", now.plusMinutes(5));

        // then
        assertEquals(2, event.getAttemptCount());
        assertEquals(1, event.getRetryCount());
    }

    @Test
    @DisplayName("heartbeat가 살아 있는 processing 이벤트는 lease 만료로 판단하지 않는다")
    void should_extend_lease_when_heartbeat_is_received() {
        // given
        LocalDateTime startedAt = LocalDateTime.now();
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        event.processing(startedAt, OWNER_ID, startedAt.plusSeconds(5));

        // when
        event.heartbeat(startedAt.plusSeconds(4), startedAt.plusSeconds(9), OWNER_ID);

        // then
        assertFalse(event.isLeaseExpired(startedAt.plusSeconds(8)));
        assertTrue(event.isLeaseExpired(startedAt.plusSeconds(9)));
    }

    @Test
    @DisplayName("progress가 일정 시간 없으면 heartbeat lease를 연장하지 않아야 한다")
    void should_detect_stale_progress() {
        // given
        LocalDateTime startedAt = LocalDateTime.now();
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        event.processing(startedAt, OWNER_ID, startedAt.plusSeconds(5));

        // when & then
        assertFalse(event.isProgressStale(startedAt.plusSeconds(4), Duration.ofSeconds(5)));
        assertTrue(event.isProgressStale(startedAt.plusSeconds(6), Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("영구 실패는 재시도하지 않고 실패 메타데이터를 기록한다")
    void should_mark_permanent_failure_without_retry() {
        // given
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        event.processing(now, OWNER_ID, now.plusMinutes(5));
        OutboxFailure failure = OutboxFailure.permanent(
                OutboxFailureCode.INVALID_PAYLOAD,
                new IllegalArgumentException("invalid payload")
        );

        // when
        event.failed(3, now.plusSeconds(1), now.plusMinutes(1), failure, OWNER_ID);

        // then
        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(OutboxFailureType.PERMANENT, event.getFailureType());
        assertEquals(OutboxFailureCode.INVALID_PAYLOAD, event.getFailureCode());
        assertEquals(IllegalArgumentException.class.getName(), event.getLastExceptionType());
        assertNotNull(event.getFirstFailedAt());
        assertNull(event.getNextRetryAt());
    }
}
