package com.dochiri.outboxpattern.application.storage.port.out;

import java.io.InputStream;
import java.util.List;

public interface MultipartFileStoragePort extends FileStoragePort {

    MultipartUploadSession initiateMultipartUpload(String objectKey, String contentType);

    MultipartUploadPart uploadPart(
            MultipartUploadSession upload,
            int partNumber,
            InputStream inputStream,
            long contentLength
    );

    void completeMultipartUpload(MultipartUploadSession upload, List<MultipartUploadPart> parts);

    void abortMultipartUpload(MultipartUploadSession upload);

    record MultipartUploadSession(String objectKey, String uploadId) {
    }

    record MultipartUploadPart(int partNumber, String eTag, long contentLength) {
    }
}
