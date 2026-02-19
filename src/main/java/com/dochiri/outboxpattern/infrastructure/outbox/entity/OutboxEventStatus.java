package com.dochiri.outboxpattern.infrastructure.outbox.entity;

public enum OutboxEventStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}