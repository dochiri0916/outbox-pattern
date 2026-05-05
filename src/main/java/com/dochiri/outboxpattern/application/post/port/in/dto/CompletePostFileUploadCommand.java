package com.dochiri.outboxpattern.application.post.port.in.dto;

public record CompletePostFileUploadCommand(
        Long postId,
        String temporaryFilePath,
        String storageKey,
        long fileSize,
        String contentType
) {
}
