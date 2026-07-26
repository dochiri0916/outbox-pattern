package com.dochiri.outboxpattern.application.outbox.port.out;

public interface OutboxPayloadSerializer {

    <T> T deserialize(String payload, Class<T> type);
}
