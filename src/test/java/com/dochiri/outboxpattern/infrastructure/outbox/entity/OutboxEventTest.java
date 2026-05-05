package com.dochiri.outboxpattern.infrastructure.outbox.entity;

import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxEventTest {

    private static final String OWNER_ID = "owner-1";

    @Test
    void should_transition_from_pending_to_processing_to_completed() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        assertTrue(event.canStartProcessing(now));

        event.processing(now, OWNER_ID);
        event.completed(now.plusSeconds(1), OWNER_ID);

        assertEquals(OutboxEventStatus.COMPLETED, event.getStatus());
        assertNull(event.getProcessingStartedAt());
        assertNull(event.getProcessingOwnerId());
        assertNull(event.getNextRetryAt());
        assertNotNull(event.getCompletedAt());
    }

    @Test
    void should_return_to_pending_when_failed_before_max_retry() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryAt = now.plusSeconds(10);
        event.processing(now, OWNER_ID);

        event.failed(5, now, nextRetryAt, "failed", OWNER_ID);

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNull(event.getProcessingStartedAt());
        assertNull(event.getProcessingOwnerId());
        assertEquals(nextRetryAt, event.getNextRetryAt());
        assertEquals("failed", event.getLastErrorMessage());
    }

    @Test
    void should_be_failed_when_retry_reaches_max() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 5; i++) {
            LocalDateTime processingAt = now.plusMinutes(i * 2L);
            event.processing(processingAt, OWNER_ID);
            event.failed(5, processingAt.plusSeconds(1), processingAt.plusMinutes(1), "failed", OWNER_ID);
        }

        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(5, event.getRetryCount());
        assertNull(event.getProcessingStartedAt());
        assertNull(event.getProcessingOwnerId());
        assertNull(event.getNextRetryAt());
        assertNotNull(event.getFailedAt());
    }

    @Test
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
    void should_throw_when_completed_by_different_processing_owner() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        event.processing(now, OWNER_ID);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> event.completed(now.plusSeconds(1), "owner-2")
        );

        assertEquals("Only current processing owner can change PROCESSING event", exception.getMessage());
    }

    @Test
    void should_prevent_stale_owner_from_completing_reprocessed_event() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime now = LocalDateTime.now();
        event.processing(now.minusMinutes(10), OWNER_ID);
        event.recoverTimedOut(5, now, now.plusSeconds(10), "PROCESSING timed out", now.minusMinutes(5));
        event.processing(now.plusSeconds(11), "owner-2");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> event.completed(now.plusSeconds(12), OWNER_ID)
        );

        assertEquals("Only current processing owner can change PROCESSING event", exception.getMessage());
    }

    @Test
    void should_detect_timed_out_processing_event() {
        OutboxEvent event = OutboxEvent.create(OutboxEventNames.POST, 1L, OutboxEventNames.POST_FILE_UPLOAD, "{}");
        LocalDateTime processingStartedAt = LocalDateTime.now().minusMinutes(10);

        event.processing(processingStartedAt, OWNER_ID);

        assertTrue(event.isProcessingTimedOut(LocalDateTime.now().minusMinutes(5)));
    }
}
