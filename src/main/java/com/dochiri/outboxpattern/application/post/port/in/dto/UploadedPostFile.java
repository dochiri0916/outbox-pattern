package com.dochiri.outboxpattern.application.post.port.in.dto;

public record UploadedPostFile(
        String temporaryFilePath,
        String originalFileName,
        long fileSize,
        String contentType
) {
}
