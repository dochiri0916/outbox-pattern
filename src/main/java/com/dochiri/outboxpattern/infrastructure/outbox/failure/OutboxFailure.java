package com.dochiri.outboxpattern.infrastructure.outbox.failure;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureCode;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureType;

import java.util.Objects;

public record OutboxFailure(
        OutboxFailureType type,
        OutboxFailureCode code,
        String exceptionType,
        String message
) {

    public OutboxFailure {
        Objects.requireNonNull(type);
        Objects.requireNonNull(code);
        Objects.requireNonNull(exceptionType);
    }

    public static OutboxFailure permanent(OutboxFailureCode code, Throwable exception) {
        return of(code, exception);
    }

    public static OutboxFailure retryable(OutboxFailureCode code, Throwable exception) {
        return of(code, exception);
    }

    public static OutboxFailure retryable(OutboxFailureCode code, String message) {
        return new OutboxFailure(
                code.type(),
                code,
                OutboxProcessingException.class.getName(),
                message
        );
    }

    private static OutboxFailure of(OutboxFailureCode code, Throwable exception) {
        Objects.requireNonNull(exception);
        Throwable rootCause = rootCause(exception);
        return new OutboxFailure(
                code.type(),
                code,
                rootCause.getClass().getName(),
                rootCause.getMessage()
        );
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
