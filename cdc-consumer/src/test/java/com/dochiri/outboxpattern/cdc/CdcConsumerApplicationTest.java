package com.dochiri.outboxpattern;

import com.dochiri.outboxpattern.application.outbox.port.in.OutboxEventHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class CdcConsumerApplicationTest {

    @Autowired
    private List<OutboxEventHandler> handlers;

    @Test
    @DisplayName("CDC Consumer가 Core Outbox Handler를 등록한다")
    void registers_core_outbox_handler() {
        // given / when

        // then
        assertFalse(handlers.isEmpty());
    }
}
