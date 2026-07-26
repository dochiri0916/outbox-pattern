package com.dochiri.outboxpattern.application.outbox.failure;

public enum OutboxFailureCode {
    HANDLER_FAILURE(OutboxFailureType.RETRYABLE),
    STORAGE_UNAVAILABLE(OutboxFailureType.RETRYABLE),
    PROCESSING_TIMEOUT(OutboxFailureType.RETRYABLE),
    UNKNOWN_FAILURE(OutboxFailureType.PERMANENT),
    INVALID_PAYLOAD(OutboxFailureType.PERMANENT),
    UNSUPPORTED_EVENT_TYPE(OutboxFailureType.PERMANENT),
    INVALID_STAGED_FILE(OutboxFailureType.PERMANENT),
    AGGREGATE_STATE_CONFLICT(OutboxFailureType.PERMANENT);

    private final OutboxFailureType type;

    OutboxFailureCode(OutboxFailureType type) {
        this.type = type;
    }

    public OutboxFailureType type() {
        return type;
    }
}
