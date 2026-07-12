package com.dochiri.outboxpattern.application.storage.port.out;

import java.io.InputStream;

public interface FileStoragePort {

    void upload(String objectKey, InputStream inputStream, long contentLength, String contentType);

    default boolean uploadIfAbsent(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        if (exists(objectKey)) {
            return false;
        }
        upload(objectKey, inputStream, contentLength, contentType);
        return true;
    }

    byte[] download(String objectKey);

    void copy(String sourceKey, String destinationKey);

    void delete(String objectKey);

    boolean exists(String objectKey);

}
