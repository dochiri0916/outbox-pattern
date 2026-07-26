package com.dochiri.outboxpattern.support;

import com.dochiri.outboxpattern.application.storage.port.out.MultipartFileStoragePort;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InMemoryFileStoragePort implements MultipartFileStoragePort {

    private final Set<String> objects = new HashSet<>();
    private final Map<String, Integer> uploadCounts = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> multipartPartUploadCounts = new HashMap<>();
    private final Map<String, MultipartUploadState> multipartUploads = new HashMap<>();
    private Integer failingMultipartPartNumber;

    @Override
    public synchronized void upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            inputStream.readAllBytes();
            objects.add(objectKey);
            uploadCounts.merge(objectKey, 1, Integer::sum);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read input stream", e);
        }
    }

    @Override
    public synchronized boolean uploadIfAbsent(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {
        if (objects.contains(objectKey)) {
            return false;
        }
        upload(objectKey, inputStream, contentLength, contentType);
        return true;
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
        return objects.contains(objectKey);
    }

    public void addObject(String objectKey) {
        objects.add(objectKey);
    }

    public void clear() {
        objects.clear();
        uploadCounts.clear();
        multipartPartUploadCounts.clear();
        multipartUploads.clear();
        failingMultipartPartNumber = null;
    }

    public int uploadCount(String objectKey) {
        return uploadCounts.getOrDefault(objectKey, 0);
    }

    public void failNextMultipartPart(int partNumber) {
        failingMultipartPartNumber = partNumber;
    }

    public int multipartPartUploadCount(String objectKey, int partNumber) {
        return multipartPartUploadCounts
                .getOrDefault(objectKey, Map.of())
                .getOrDefault(partNumber, 0);
    }

    public boolean shouldFailMultipartPart(int partNumber) {
        if (!Integer.valueOf(partNumber).equals(failingMultipartPartNumber)) {
            return false;
        }
        failingMultipartPartNumber = null;
        return true;
    }

    public void recordMultipartPartUpload(String objectKey, int partNumber) {
        multipartPartUploadCounts
                .computeIfAbsent(objectKey, ignored -> new HashMap<>())
                .merge(partNumber, 1, Integer::sum);
    }

    @Override
    public synchronized MultipartUploadSession initiateMultipartUpload(String objectKey, String contentType) {
        String uploadId = UUID.randomUUID().toString();
        multipartUploads.put(uploadId, new MultipartUploadState());
        return new MultipartUploadSession(objectKey, uploadId);
    }

    @Override
    public synchronized MultipartUploadPart uploadPart(
            MultipartUploadSession upload,
            int partNumber,
            InputStream inputStream,
            long contentLength
    ) {
        if (shouldFailMultipartPart(partNumber)) {
            throw new IllegalStateException("Simulated multipart upload failure");
        }
        MultipartUploadState state = multipartUploads.get(upload.uploadId());
        if (state == null) {
            throw new IllegalStateException("Multipart upload not found: " + upload.uploadId());
        }
        try {
            byte[] content = inputStream.readAllBytes();
            if (content.length != contentLength) {
                throw new IllegalStateException("Multipart content length mismatch");
            }
            state.parts.put(partNumber, content);
            recordMultipartPartUpload(upload.objectKey(), partNumber);
            return new MultipartUploadPart(partNumber, "etag-" + partNumber, contentLength);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read multipart content", e);
        }
    }

    @Override
    public synchronized void completeMultipartUpload(
            MultipartUploadSession upload,
            java.util.List<MultipartUploadPart> parts
    ) {
        MultipartUploadState state = multipartUploads.remove(upload.uploadId());
        if (state == null) {
            throw new IllegalStateException("Multipart upload not found: " + upload.uploadId());
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (MultipartUploadPart part : parts) {
                output.write(state.parts.get(part.partNumber()));
            }
            objects.add(upload.objectKey());
            uploadCounts.merge(upload.objectKey(), 1, Integer::sum);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to complete multipart content", e);
        }
    }

    @Override
    public synchronized void abortMultipartUpload(MultipartUploadSession upload) {
        multipartUploads.remove(upload.uploadId());
    }

    private static class MultipartUploadState {

        private final Map<Integer, byte[]> parts = new HashMap<>();

        private MultipartUploadState() {
        }
    }
}
