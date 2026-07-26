package com.dochiri.outboxpattern.application.outbox.model;

public record OutboxEffectPart(
        Long outboxEventId,
        int partNumber,
        String eTag,
        long contentLength
) {
}
