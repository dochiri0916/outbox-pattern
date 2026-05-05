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
        if (alreadyCompleted(command)) {
            return;
        }

        if (!fileStoragePort.exists(command.storageKey())) {
            fileStoragePort.copy(command.temporaryFilePath(), command.storageKey());
        }

        if (!fileStoragePort.exists(command.storageKey())) {
            throw new IllegalStateException("Failed to upload file to storage: " + command.storageKey());
        }

        fileStoragePort.delete(command.temporaryFilePath());

        if (alreadyCompleted(command)) {
            return;
        }

        postFileRepository.save(PostFile.create(
                command.postId(),
                command.storageKey(),
                command.fileSize(),
                command.contentType()
        ));
    }

    private boolean alreadyCompleted(CompletePostFileUploadCommand command) {
        return postFileRepository.findByStorageKey(command.storageKey())
                .map(postFile -> {
                    if (postFile.hasSameMetadata(command.postId(), command.fileSize(), command.contentType())) {
                        return true;
                    }
                    throw new IllegalStateException(
                            "PostFile metadata conflicts with storageKey: " + command.storageKey()
                    );
                })
                .orElse(false);
    }

}
