package com.dochiri.outboxpattern.adapter.out.storage.s3;

import com.dochiri.outboxpattern.application.storage.port.out.MultipartFileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
public class S3FileStorageAdapter implements MultipartFileStoragePort {

    private final S3Client s3Client;
    private final AwsS3Properties awsS3Properties;

    @Override
    public void upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        s3Client.putObject(putObjectRequest(objectKey, contentLength, contentType), RequestBody.fromInputStream(inputStream, contentLength));
    }

    @Override
    public boolean uploadIfAbsent(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            s3Client.putObject(
                    conditionalPutObjectRequest(objectKey, contentLength, contentType),
                    RequestBody.fromInputStream(inputStream, contentLength)
            );
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 412) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public MultipartUploadSession initiateMultipartUpload(String objectKey, String contentType) {
        String uploadId = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build()
        ).uploadId();
        return new MultipartUploadSession(objectKey, uploadId);
    }

    @Override
    public MultipartUploadPart uploadPart(
            MultipartUploadSession upload,
            int partNumber,
            InputStream inputStream,
            long contentLength
    ) {
        UploadPartResponse response = s3Client.uploadPart(
                UploadPartRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(upload.objectKey())
                        .uploadId(upload.uploadId())
                        .partNumber(partNumber)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(inputStream, contentLength)
        );
        return new MultipartUploadPart(partNumber, response.eTag(), contentLength);
    }

    @Override
    public void completeMultipartUpload(MultipartUploadSession upload, List<MultipartUploadPart> parts) {
        s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(upload.objectKey())
                        .uploadId(upload.uploadId())
                        .multipartUpload(
                                CompletedMultipartUpload.builder()
                                        .parts(parts.stream()
                                                .map(part -> CompletedPart.builder()
                                                        .partNumber(part.partNumber())
                                                        .eTag(part.eTag())
                                                        .build())
                                                .toList())
                                        .build()
                        )
                        .build()
        );
    }

    @Override
    public void abortMultipartUpload(MultipartUploadSession upload) {
        s3Client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                        .bucket(awsS3Properties.bucket())
                        .key(upload.objectKey())
                        .uploadId(upload.uploadId())
                        .build()
        );
    }

    private PutObjectRequest putObjectRequest(String objectKey, long contentLength, String contentType) {
        return PutObjectRequest.builder()
                .bucket(awsS3Properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
    }

    private PutObjectRequest conditionalPutObjectRequest(String objectKey, long contentLength, String contentType) {
        return PutObjectRequest.builder()
                .bucket(awsS3Properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .overrideConfiguration(builder -> builder.putHeader("If-None-Match", "*"))
                .build();
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
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

}
