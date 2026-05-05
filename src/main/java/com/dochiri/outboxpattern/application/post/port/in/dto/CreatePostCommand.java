package com.dochiri.outboxpattern.application.post.port.in.dto;

public record CreatePostCommand(
        String title,
        String content,
        String temporaryFilePath,
        String originalFileName,
        long fileSize,
        String contentType
) {
}
