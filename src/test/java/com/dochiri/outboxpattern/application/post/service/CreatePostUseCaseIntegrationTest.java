package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.infrastructure.adapter.out.persistence.PostJpaRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class CreatePostUseCaseIntegrationTest {

    @Autowired
    private CreatePostUseCase createPostUseCase;

    @Autowired
    private PostJpaRepository postRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        postRepository.deleteAll();
    }

    @Test
    void should_save_post_and_outbox_event_in_single_use_case_execution() {
        CreatePostCommand command = new CreatePostCommand(
                "title",
                "content",
                "temporary/path/file.txt",
                "file.txt",
                100L,
                "text/plain"
        );

        CreatePostResult output = createPostUseCase.execute(command);

        assertTrue(postRepository.findById(output.postId()).isPresent());

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(output.postId(), events.get(0).getAggregateId());
        assertEquals(OutboxEventNames.POST_FILE_UPLOAD, events.get(0).getEventType());
    }
}
