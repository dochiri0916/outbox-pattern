package com.dochiri.outboxpattern.infrastructure.outbox.handler.post;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.post.port.in.CompletePostFileUploadUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CompletePostFileUploadCommand;
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

        completePostFileUploadUseCase.complete(new CompletePostFileUploadCommand(
                event.postId(),
                event.temporaryFilePath(),
                event.storageKey(),
                event.fileSize(),
                event.contentType()
        ));
    }

}
