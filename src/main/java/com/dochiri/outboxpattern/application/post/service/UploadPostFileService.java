package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.post.port.in.UploadPostFileUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.UploadPostFileCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.UploadedPostFile;
import com.dochiri.outboxpattern.application.storage.port.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadPostFileService implements UploadPostFileUseCase {

    private final FileStoragePort fileStoragePort;

    @Override
    public UploadedPostFile upload(UploadPostFileCommand command) {
        String temporaryStorageKey = generateTemporaryStorageKey(command.originalFileName());

        fileStoragePort.upload(
                temporaryStorageKey,
                command.inputStream(),
                command.fileSize(),
                command.contentType()
        );

        return new UploadedPostFile(
                temporaryStorageKey,
                command.originalFileName(),
                command.fileSize(),
                command.contentType()
        );
    }

    private String generateTemporaryStorageKey(String originalFileName) {
        return "temporary/%s/%s".formatted(UUID.randomUUID(), originalFileName);
    }

}
