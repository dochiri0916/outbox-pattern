package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.event.port.out.EventPublisher;
import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.application.post.port.out.PostRepository;
import com.dochiri.outboxpattern.domain.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    @Override
    public CreatePostResult execute(CreatePostCommand command) {
        Post savedPost = postRepository.save(Post.create(command.title(), command.content()));

        String storageKey = generateStorageKey(
                savedPost.getId(),
                command.temporaryFilePath(),
                command.originalFileName()
        );
        eventPublisher.publish(new PostFileUploadRequestedEvent(
                savedPost.getId(),
                command.temporaryFilePath(),
                storageKey,
                command.fileSize(),
                command.contentType()
        ));

        return new CreatePostResult(savedPost.getId());
    }

    private String generateStorageKey(Long postId, String temporaryFilePath, String originalFileName) {
        String stableFileId = UUID.nameUUIDFromBytes(
                "%d:%s".formatted(postId, temporaryFilePath).getBytes(StandardCharsets.UTF_8)
        ).toString();
        return "post/%d/%s/%s".formatted(postId, stableFileId, originalFileName);
    }

}
