package com.dochiri.outboxpattern.application.blog;

import com.dochiri.outboxpattern.common.outbox.PostFileUploadPayload;
import com.dochiri.outboxpattern.domain.blog.Post;
import com.dochiri.outboxpattern.domain.blog.PostRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.AggregateType;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePostUseCase {

    private final PostRepository postRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    @Transactional
    public Output execute(Input input) {
        Post savedPost = postRepository.save(Post.create(input.title, input.content));

        String storageKey = "post/%d/%s".formatted(savedPost.getId(), input.originalFileName);
        PostFileUploadPayload payload = new PostFileUploadPayload(savedPost.getId(), input.temporaryFilePath, storageKey, input.fileSize, input.contentType);

        outboxEventRecorder.record(
                AggregateType.POST, savedPost.getId(), OutboxEventType.POST_FILE_UPLOAD, payload
        );

        return new Output(savedPost.getId());
    }

    public record Input(String title, String content, String temporaryFilePath, String originalFileName, long fileSize, String contentType) {

    }

    public record Output(Long postId) {

    }

}
