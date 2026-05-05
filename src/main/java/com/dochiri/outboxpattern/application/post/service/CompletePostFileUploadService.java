package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.post.port.in.CompletePostFileUploadUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CompletePostFileUploadCommand;
import com.dochiri.outboxpattern.application.post.port.out.PostFileRepository;
import com.dochiri.outboxpattern.application.storage.port.out.FileStoragePort;
import com.dochiri.outboxpattern.domain.PostFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompletePostFileUploadService implements CompletePostFileUploadUseCase {

    private final PostFileRepository postFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    @Override
    public void complete(CompletePostFileUploadCommand command) {
        fileStoragePort.copy(command.temporaryFilePath(), command.storageKey());

        if (!fileStoragePort.exists(command.storageKey())) {
            throw new IllegalStateException("Failed to upload file to storage: " + command.storageKey());
        }

        fileStoragePort.delete(command.temporaryFilePath());

        postFileRepository.save(PostFile.create(
                command.postId(),
                command.storageKey(),
                command.fileSize(),
                command.contentType()
        ));
    }

}
