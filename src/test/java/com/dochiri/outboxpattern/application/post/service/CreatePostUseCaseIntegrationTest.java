package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.infrastructure.adapter.out.persistence.PostJpaRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import(TestStorageConfiguration.class)
class CreatePostUseCaseIntegrationTest {

    @Autowired
    private CreatePostUseCase createPostUseCase;

    @Autowired
    private PostJpaRepository postRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private InMemoryFileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        postRepository.deleteAll();
        fileStoragePort.clear();
    }

    @Test
    void should_save_post_and_outbox_event_in_single_use_case_execution() {
        CreatePostCommand command = new CreatePostCommand(
                "title",
                "content",
                new ByteArrayInputStream("test content".getBytes(StandardCharsets.UTF_8)),
                "file.txt",
                "test content".getBytes(StandardCharsets.UTF_8).length,
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
