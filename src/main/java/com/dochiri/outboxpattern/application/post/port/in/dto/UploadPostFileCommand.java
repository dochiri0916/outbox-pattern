package com.dochiri.outboxpattern.application.post.port.in.dto;

import java.io.InputStream;

public record UploadPostFileCommand(
        String originalFileName,
        InputStream inputStream,
        long fileSize,
        String contentType
) {
}
