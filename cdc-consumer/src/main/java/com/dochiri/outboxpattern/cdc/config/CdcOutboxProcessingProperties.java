package com.dochiri.outboxpattern.cdc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.cdc.processing")
public record CdcOutboxProcessingProperties(long multipartPartSizeBytes) {

    private static final long DEFAULT_MULTIPART_PART_SIZE_BYTES = 5 * 1024 * 1024L;

    public CdcOutboxProcessingProperties {
        if (multipartPartSizeBytes <= 0) {
            multipartPartSizeBytes = DEFAULT_MULTIPART_PART_SIZE_BYTES;
        }
    }

}
