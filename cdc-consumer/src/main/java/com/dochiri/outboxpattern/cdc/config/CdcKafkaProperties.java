package com.dochiri.outboxpattern.cdc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.cdc.kafka")
public record CdcKafkaProperties(String bootstrapServers) {

    public CdcKafkaProperties {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = "localhost:29092";
        }
    }

}
