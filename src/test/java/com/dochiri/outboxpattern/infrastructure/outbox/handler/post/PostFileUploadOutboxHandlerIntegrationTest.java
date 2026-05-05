package com.dochiri.outboxpattern.infrastructure.outbox.handler.post;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.infrastructure.adapter.out.persistence.PostFileJpaRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxEventContext;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import(TestStorageConfiguration.class)
class PostFileUploadOutboxHandlerIntegrationTest {

    @Autowired
    private PostFileUploadOutboxHandler handler;

    @Autowired
    private OutboxPayloadSerializer outboxPayloadSerializer;

    @Autowired
    private PostFileJpaRepository postFileRepository;

    @Autowired
    private InMemoryFileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        postFileRepository.deleteAll();
        fileStoragePort.clear();
    }

    @Test
    void should_save_post_file_when_copy_and_exists_succeed() {
        String temporaryPath = "temporary/1/file.txt";
        String finalPath = "post/1/file.txt";
        fileStoragePort.addObject(temporaryPath);

        OutboxEventContext eventContext = createEventContext(1L, temporaryPath, finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    void should_throw_and_not_save_when_exists_check_fails() {
        String temporaryPath = "temporary/1/file.txt";
        String finalPath = "post/1/file.txt";
        fileStoragePort.addObject(temporaryPath);
        fileStoragePort.setForcedExists(false);

        OutboxEventContext eventContext = createEventContext(1L, temporaryPath, finalPath);

        assertThrows(IllegalStateException.class, () -> handler.handle(eventContext));
        assertEquals(0, postFileRepository.count());
    }

    private OutboxEventContext createEventContext(Long postId, String temporaryPath, String finalPath) {
        PostFileUploadRequestedEvent payload = new PostFileUploadRequestedEvent(
                postId,
                temporaryPath,
                finalPath,
                100L,
                "text/plain"
        );
        String serializedPayload = outboxPayloadSerializer.serialize(payload);
        return new OutboxEventContext(1L, OutboxEventNames.POST_FILE_UPLOAD, serializedPayload, "owner-1");
    }
}
