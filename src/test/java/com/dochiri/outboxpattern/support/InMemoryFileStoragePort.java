package com.dochiri.outboxpattern.support;

import com.dochiri.outboxpattern.application.storage.port.FileStoragePort;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class InMemoryFileStoragePort implements FileStoragePort {

    private final Set<String> objects = new HashSet<>();
    private Boolean forcedExists;
    private boolean throwOnCopy;

    @Override
    public void upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            inputStream.readAllBytes();
            objects.add(objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read input stream", e);
        }
    }

    @Override
    public byte[] download(String objectKey) {
        if (!objects.contains(objectKey)) {
            throw new IllegalStateException("Object not found: " + objectKey);
        }
        return new byte[0];
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        if (throwOnCopy) {
            throw new IllegalStateException("Copy failed intentionally");
        }
        if (!objects.contains(sourceKey)) {
            throw new IllegalStateException("Source object not found: " + sourceKey);
        }
        objects.add(destinationKey);
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        if (forcedExists != null) {
            return forcedExists;
        }
        return objects.contains(objectKey);
    }

    public void addObject(String objectKey) {
        objects.add(objectKey);
    }

    public void setForcedExists(Boolean forcedExists) {
        this.forcedExists = forcedExists;
    }

    public void setThrowOnCopy(boolean throwOnCopy) {
        this.throwOnCopy = throwOnCopy;
    }

    public void clear() {
        objects.clear();
        forcedExists = null;
        throwOnCopy = false;
    }
}
