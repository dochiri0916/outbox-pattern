package com.dochiri.outboxpattern.infrastructure.outbox.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxEventTest {

    @Test
    void should_transition_from_pending_to_processing_to_completed() {
        OutboxEvent event = OutboxEvent.create(AggregateType.POST, 1L, OutboxEventType.POST_FILE_UPLOAD, "{}");

        assertTrue(event.canStartProcessing());

        event.processing();
        event.completed();

        assertEquals(OutboxEventStatus.COMPLETED, event.getStatus());
    }

    @Test
    void should_return_to_pending_when_failed_before_max_retry() {
        OutboxEvent event = OutboxEvent.create(AggregateType.POST, 1L, OutboxEventType.POST_FILE_UPLOAD, "{}");
        event.processing();

        event.failed(5);

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
    }

    @Test
    void should_be_failed_when_retry_reaches_max() {
        OutboxEvent event = OutboxEvent.create(AggregateType.POST, 1L, OutboxEventType.POST_FILE_UPLOAD, "{}");

        for (int i = 0; i < 5; i++) {
            event.processing();
            event.failed(5);
        }

        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(5, event.getRetryCount());
    }

    @Test
    void should_throw_when_failed_called_outside_processing() {
        OutboxEvent event = OutboxEvent.create(AggregateType.POST, 1L, OutboxEventType.POST_FILE_UPLOAD, "{}");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> event.failed(5));

        assertEquals("Only PROCESSING events can be marked as failed", exception.getMessage());
    }
}
