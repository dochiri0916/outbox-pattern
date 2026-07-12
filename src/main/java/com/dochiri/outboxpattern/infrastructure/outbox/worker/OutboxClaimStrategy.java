package com.dochiri.outboxpattern.infrastructure.outbox.worker;

public enum OutboxClaimStrategy {
    SKIP_LOCKED,
    PESSIMISTIC_WRITE
}
