package com.example.outboxpattern.infrastructure.storage.s3;

import com.example.outboxpattern.application.storage.port.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class S3FileStorageAdapter implements FileStoragePort {

    private final S3Client s3Client;
    private final AwsS3Properties awsS3Properties;

    @Override
    public void upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build();

        s3Client.putObject(
                putObjectRequest,
                RequestBody.fromInputStream(inputStream, contentLength)
        );
    }

    @Override
    public byte[] download(String objectKey) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(objectKey)
                        .build()
        ).asByteArray();
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                .sourceBucket(awsS3Properties.bucket())
                .sourceKey(sourceKey)
                .destinationBucket(awsS3Properties.bucket())
                .destinationKey(destinationKey)
                .build();
        s3Client.copyObject(copyObjectRequest);
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(awsS3Properties.bucket())
                .key(objectKey)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(
                    b -> b.bucket(awsS3Properties.bucket()).key(objectKey)
            );
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

}