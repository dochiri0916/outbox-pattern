package com.dochiri.outboxpattern.common.outbox;

public record PostFileUploadPayload(
        Long postId,
        String temporaryFilePath,
        String storageKey,
        long fileSize,
        String contentType
) {

}
