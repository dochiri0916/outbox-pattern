package com.dochiri.outboxpattern.application.post.port.in.dto;

import java.io.InputStream;

public record CreatePostCommand(
        String title,
        String content,
        InputStream inputStream,
        String originalFileName,
        long fileSize,
        String contentType
) {
}
