package com.dochiri.outboxpattern.application.outbox.failure;

import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureCode;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureType;

import java.util.Objects;

public final class OutboxProcessingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final OutboxFailureType failureType;
    private final OutboxFailureCode failureCode;

    private OutboxProcessingException(
            OutboxFailureType failureType,
            OutboxFailureCode failureCode,
            Throwable cause
    ) {
        super(failureCode.name(), cause);
        this.failureType = Objects.requireNonNull(failureType);
        this.failureCode = Objects.requireNonNull(failureCode);
    }

    public static OutboxProcessingException permanent(OutboxFailureCode failureCode, Throwable cause) {
        return create(OutboxFailureType.PERMANENT, failureCode, cause);
    }

    public static OutboxProcessingException retryable(OutboxFailureCode failureCode, Throwable cause) {
        return create(OutboxFailureType.RETRYABLE, failureCode, cause);
    }

    public OutboxFailure failure() {
        return new OutboxFailure(
                failureType,
                failureCode,
                rootCause().getClass().getName(),
                rootCause().getMessage()
        );
    }

    private static OutboxProcessingException create(
            OutboxFailureType failureType,
            OutboxFailureCode failureCode,
            Throwable cause
    ) {
        Objects.requireNonNull(failureCode);
        if (failureCode.type() != failureType) {
            throw new IllegalArgumentException("Failure type does not match failure code");
        }
        return new OutboxProcessingException(failureType, failureCode, cause);
    }

    private Throwable rootCause() {
        Throwable rootCause = getCause();
        while (rootCause != null && rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause == null ? this : rootCause;
    }
}
