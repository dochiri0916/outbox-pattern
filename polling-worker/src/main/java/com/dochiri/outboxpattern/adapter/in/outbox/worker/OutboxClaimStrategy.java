package com.dochiri.outboxpattern.adapter.in.outbox.worker;

public enum OutboxClaimStrategy {
    SKIP_LOCKED,
    PESSIMISTIC_WRITE
}
