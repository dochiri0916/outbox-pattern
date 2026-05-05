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

@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

    private final PostRepository postRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    @Override
    public CreatePostResult execute(CreatePostCommand command) {
        Post savedPost = postRepository.save(Post.create(command.title(), command.content()));

        String storageKey = "post/%d/%s".formatted(savedPost.getId(), command.originalFileName());
        eventPublisher.publish(new PostFileUploadRequestedEvent(
                savedPost.getId(),
                command.temporaryFilePath(),
                storageKey,
                command.fileSize(),
                command.contentType()
        ));

        return new CreatePostResult(savedPost.getId());
    }

}
