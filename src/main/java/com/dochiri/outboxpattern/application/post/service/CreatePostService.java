package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.application.storage.port.out.FileStoragePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

    private final FileStoragePort fileStoragePort;
    private final CreatePostTransactional transactional;

    @Override
    public CreatePostResult execute(CreatePostCommand command) {
        String temporaryKey = generateTemporaryStorageKey(command.originalFileName());
        fileStoragePort.upload(
                temporaryKey,
                command.inputStream(),
                command.fileSize(),
                command.contentType()
        );
        return transactional.create(command, temporaryKey);
    }

    private String generateTemporaryStorageKey(String originalFileName) {
        return "temporary/%s/%s".formatted(UUID.randomUUID(), originalFileName);
    }

}
