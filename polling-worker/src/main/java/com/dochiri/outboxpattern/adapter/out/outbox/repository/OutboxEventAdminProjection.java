package com.dochiri.outboxpattern.adapter.out.outbox.repository;

import java.time.LocalDateTime;

public interface OutboxEventAdminProjection {

    Long getId();

    String getAggregateType();

    Long getAggregateId();

    String getEventType();

    String getSourceStatus();

    String getEffectiveStatus();

    int getAttemptCount();

    int getRetryCount();

    String getFailureType();

    String getFailureCode();

    String getLastExceptionType();

    LocalDateTime getFirstFailedAt();

    LocalDateTime getAttemptStartedAt();

    LocalDateTime getLastProgressAt();

    LocalDateTime getLeaseUntil();

    LocalDateTime getNextRetryAt();

    LocalDateTime getCreatedAt();

    LocalDateTime getCompletedAt();

    LocalDateTime getFailedAt();

    String getLastErrorMessage();
}
