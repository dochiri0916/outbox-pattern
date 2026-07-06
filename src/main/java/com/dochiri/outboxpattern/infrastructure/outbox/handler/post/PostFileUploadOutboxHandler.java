package com.dochiri.outboxpattern.infrastructure.outbox.handler.post;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.post.port.in.CompletePostFileUploadUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CompletePostFileUploadCommand;
import com.dochiri.outboxpattern.application.post.port.out.PostFileRepository;
import com.dochiri.outboxpattern.application.storage.port.out.FileStoragePort;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxEventContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostFileUploadOutboxHandler implements OutboxEventHandler {

    private final CompletePostFileUploadUseCase completePostFileUploadUseCase;
    private final OutboxPayloadSerializer outboxPayloadSerializer;
    private final FileStoragePort fileStoragePort;
    private final PostFileRepository postFileRepository;

    @Override
    public boolean supports(String eventType) {
        return OutboxEventNames.POST_FILE_UPLOAD.equals(eventType);
    }

    @Override
    public void handle(OutboxEventContext eventContext) {
        PostFileUploadRequestedEvent event = outboxPayloadSerializer.deserialize(
                eventContext.payload(),
                PostFileUploadRequestedEvent.class
        );

        if (alreadyCompleted(event)) {
            return;
        }

        copyToPermanentStorage(event);
        fileStoragePort.delete(event.temporaryFilePath());

        completePostFileUploadUseCase.complete(new CompletePostFileUploadCommand(
                event.postId(),
                event.temporaryFilePath(),
                event.storageKey(),
                event.fileSize(),
                event.contentType()
        ));
    }

    private boolean alreadyCompleted(PostFileUploadRequestedEvent event) {
        return postFileRepository.findByStorageKey(event.storageKey())
                .map(postFile -> {
                    if (postFile.hasSameMetadata(event.postId(), event.fileSize(), event.contentType())) {
                        return true;
                    }
                    throw new IllegalStateException(
                            "PostFile metadata conflicts with storageKey: " + event.storageKey()
                    );
                })
                .orElse(false);
    }

    private void copyToPermanentStorage(PostFileUploadRequestedEvent event) {
        if (!fileStoragePort.exists(event.storageKey())) {
            fileStoragePort.copy(event.temporaryFilePath(), event.storageKey());
        }

        if (!fileStoragePort.exists(event.storageKey())) {
            throw new IllegalStateException("Failed to upload file to storage: " + event.storageKey());
        }
    }

}
