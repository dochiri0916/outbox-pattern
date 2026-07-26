package com.dochiri.outboxpattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "outbox.worker.enabled=false"
})
class OutboxPatternApplicationTests {

    @Test
    void contextLoads() {
    }

}
