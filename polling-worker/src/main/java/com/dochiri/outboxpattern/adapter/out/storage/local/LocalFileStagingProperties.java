package com.dochiri.outboxpattern.adapter.out.storage.local;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.staging")
public record LocalFileStagingProperties(String dir) {

    public LocalFileStagingProperties {
        if (dir == null || dir.isBlank()) {
            dir = "/tmp/outbox-staging";
        }
    }

}
