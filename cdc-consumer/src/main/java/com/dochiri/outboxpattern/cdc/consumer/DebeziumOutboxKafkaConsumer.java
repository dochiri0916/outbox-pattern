package com.dochiri.outboxpattern.cdc.consumer;

import com.dochiri.outboxpattern.application.outbox.port.in.OutboxMessageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DebeziumOutboxKafkaConsumer {

    private final DebeziumOutboxRecordParser parser;
    private final OutboxMessageProcessor processor;

    @KafkaListener(
            topics = "${outbox.cdc.topic:outbox-pattern.public.outbox_event}",
            groupId = "${outbox.cdc.group-id:outbox-cdc-consumer}",
            containerFactory = "cdcKafkaListenerContainerFactory"
    )
    public void consume(String value) {
        DebeziumOutboxRecord record = parser.parse(value);
        if (!"c".equals(record.op()) || record.after() == null) {
            return;
        }
        log.debug("Received Debezium outbox event. id={}, eventType={}",
                record.after().id(), record.after().eventType());
        processor.process(record.after());
    }
}
