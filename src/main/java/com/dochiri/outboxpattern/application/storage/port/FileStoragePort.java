package com.dochiri.outboxpattern.application.storage.port;

import java.io.InputStream;

public interface FileStoragePort {

    void upload(String objectKey, InputStream inputStream, long contentLength, String contentType);

    byte[] download(String objectKey);

    void copy(String sourceKey, String destinationKey);

    void delete(String objectKey);

    boolean exists(String objectKey);

}