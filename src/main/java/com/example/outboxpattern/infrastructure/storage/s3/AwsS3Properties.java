package com.example.outboxpattern.infrastructure.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record AwsS3Properties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket
) {

}