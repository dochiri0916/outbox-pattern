package com.dochiri.outboxpattern.application.post.event;

import com.dochiri.outboxpattern.application.event.ApplicationEvent;

public record PostFileUploadRequestedEvent(
        Long postId,
        String temporaryFilePath,
        String storageKey,
        long fileSize,
        String contentType
) implements ApplicationEvent {

}
