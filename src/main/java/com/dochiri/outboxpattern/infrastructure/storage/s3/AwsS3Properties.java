package com.dochiri.outboxpattern.infrastructure.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "aws.s3")
public record AwsS3Properties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Duration apiCallTimeout
) {

    public AwsS3Properties {
        if (apiCallTimeout == null || apiCallTimeout.isZero() || apiCallTimeout.isNegative()) {
            apiCallTimeout = Duration.ofMinutes(2);
        }
    }

}
