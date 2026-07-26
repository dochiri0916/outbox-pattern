package com.dochiri.outboxpattern.cdc.config;

import com.dochiri.outboxpattern.application.outbox.port.out.OutboxProcessingProperties;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxProgressPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CdcOutboxProcessingConfiguration {

    @Bean
    OutboxProcessingProperties outboxProcessingProperties(
            CdcOutboxProcessingProperties processingProperties
    ) {
        return processingProperties::multipartPartSizeBytes;
    }

    @Bean
    OutboxProgressPort outboxProgressPort() {
        return (outboxEventId, processingOwnerId) -> {
            // CDC의 처리 소유권과 재처리는 Kafka consumer group/offset이 담당한다.
        };
    }
}
