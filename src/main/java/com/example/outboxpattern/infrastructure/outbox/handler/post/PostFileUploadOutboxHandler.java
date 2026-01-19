package com.example.outboxpattern.infrastructure.outbox.handler.post;

import com.example.outboxpattern.application.blog.CreatePostUseCase;
import com.example.outboxpattern.application.storage.port.FileStoragePort;
import com.example.outboxpattern.domain.blog.PostFile;
import com.example.outboxpattern.domain.blog.PostFileRepository;
import com.example.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.example.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.example.outboxpattern.infrastructure.outbox.entity.OutboxEventType;
import com.example.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostFileUploadOutboxHandler implements OutboxEventHandler {

    private final PostFileRepository postFileRepository;
    private final FileStoragePort fileStoragePort;
    private final OutboxPayloadSerializer outboxPayloadSerializer;

    @Override
    public boolean supports(OutboxEventType eventType) {
        return eventType.equals(OutboxEventType.POST_FILE_UPLOAD);
    }

    @Override
    public void handle(OutboxEvent event) {
        CreatePostUseCase.PostFileUploadPayload payload = outboxPayloadSerializer.deserialize(
                event.getPayload(),
                CreatePostUseCase.PostFileUploadPayload.class
        );

        fileStoragePort.copy(payload.temporaryFilePath(), payload.storageKey());

        if (!fileStoragePort.exists(payload.storageKey())) {
            throw new IllegalStateException("Failed to upload file to storage: " + payload.storageKey());
        }

        fileStoragePort.delete(payload.temporaryFilePath());

        postFileRepository.save(PostFile.create(
                payload.postId(),
                payload.storageKey(),
                payload.fileSize(),
                payload.contentType()
        ));
    }

}