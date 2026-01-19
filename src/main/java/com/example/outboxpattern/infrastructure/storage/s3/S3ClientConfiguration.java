package com.example.outboxpattern.infrastructure.storage.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class S3ClientConfiguration {

    private final AwsS3Properties awsS3Properties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(awsS3Properties.endpoint()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        awsS3Properties.accessKey(),
                                        awsS3Properties.secretKey()
                                )
                        )
                )
                .region(Region.of(awsS3Properties.region()))
                .forcePathStyle(true)
                .build();
    }

}