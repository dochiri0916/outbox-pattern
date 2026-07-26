package com.dochiri.outboxpattern.application.outbox.failure;

import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureCode;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class OutboxFailureClassifier {

    public OutboxFailure classify(Throwable exception) {
        if (exception instanceof OutboxProcessingException processingException) {
            return processingException.failure();
        }
        Throwable rootCause = rootCause(exception);
        if (rootCause instanceof IOException
                || rootCause instanceof TimeoutException
                || rootCause instanceof TransientDataAccessException) {
            return OutboxFailure.retryable(OutboxFailureCode.STORAGE_UNAVAILABLE, rootCause);
        }
        return OutboxFailure.permanent(OutboxFailureCode.UNKNOWN_FAILURE, rootCause);
    }

    private Throwable rootCause(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
