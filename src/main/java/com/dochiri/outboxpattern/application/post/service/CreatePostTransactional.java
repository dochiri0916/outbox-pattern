package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.event.port.out.EventPublisher;
import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.application.post.port.out.PostRepository;
import com.dochiri.outboxpattern.domain.Post;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class CreatePostTransactional {

    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    CreatePostResult create(CreatePostCommand command, String localFilePath) {
        Post savedPost = postRepository.save(Post.create(command.title(), command.content()));

        String storageKey = generateStorageKey(
                savedPost.getId(),
                localFilePath,
                command.originalFileName()
        );
        eventPublisher.publish(new PostFileUploadRequestedEvent(
                savedPost.getId(),
                localFilePath,
                storageKey,
                command.fileSize(),
                command.contentType()
        ));

        return new CreatePostResult(savedPost.getId());
    }

    private String generateStorageKey(Long postId, String localFilePath, String originalFileName) {
        String stableFileId = UUID.nameUUIDFromBytes(
                "%d:%s".formatted(postId, localFilePath).getBytes(StandardCharsets.UTF_8)
        ).toString();
        return "post/%d/%s/%s".formatted(postId, stableFileId, originalFileName);
    }

}
