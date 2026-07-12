package com.dochiri.outboxpattern.infrastructure.outbox.failure;

import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureCode;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxFailureClassifierTest {

    private final OutboxFailureClassifier classifier = new OutboxFailureClassifier();

    @Test
    @DisplayName("영구 실패 예외는 영구 오류와 원인 코드를 보존한다")
    void should_preserve_permanent_failure_metadata() {
        // given
        IllegalArgumentException cause = new IllegalArgumentException("invalid payload");
        OutboxProcessingException exception = OutboxProcessingException.permanent(
                OutboxFailureCode.INVALID_PAYLOAD,
                cause
        );

        // when
        OutboxFailure failure = classifier.classify(exception);

        // then
        assertEquals(OutboxFailureType.PERMANENT, failure.type());
        assertEquals(OutboxFailureCode.INVALID_PAYLOAD, failure.code());
        assertEquals(IllegalArgumentException.class.getName(), failure.exceptionType());
    }

    @Test
    @DisplayName("명시되지 않은 예외는 영구 오류로 분류한다")
    void should_classify_unknown_exception_as_permanent_failure() {
        // given
        RuntimeException exception = new RuntimeException("temporary failure");

        // when
        OutboxFailure failure = classifier.classify(exception);

        // then
        assertEquals(OutboxFailureType.PERMANENT, failure.type());
        assertEquals(OutboxFailureCode.UNKNOWN_FAILURE, failure.code());
        assertEquals(RuntimeException.class.getName(), failure.exceptionType());
    }

    @Test
    @DisplayName("일시적인 I/O 오류는 storage 재시도로 분류한다")
    void should_classify_transient_io_failure_as_retryable_storage_failure() {
        // given
        IOException cause = new IOException("storage unavailable");
        RuntimeException exception = new RuntimeException(cause);

        // when
        OutboxFailure failure = classifier.classify(exception);

        // then
        assertEquals(OutboxFailureType.RETRYABLE, failure.type());
        assertEquals(OutboxFailureCode.STORAGE_UNAVAILABLE, failure.code());
        assertEquals(IOException.class.getName(), failure.exceptionType());
    }
}
