package com.dochiri.outboxpattern.application.outbox.port.out;

public interface OutboxProgressPort {

    void recordProgress(Long outboxEventId, String processingOwnerId);
}
