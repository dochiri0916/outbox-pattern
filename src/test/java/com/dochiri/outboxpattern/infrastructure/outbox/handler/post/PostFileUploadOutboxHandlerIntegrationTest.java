package com.dochiri.outboxpattern.infrastructure.outbox.handler.post;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.domain.PostFile;
import com.dochiri.outboxpattern.infrastructure.adapter.out.persistence.PostFileJpaRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxEventContext;
import com.dochiri.outboxpattern.infrastructure.storage.local.LocalFileStaging;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.staging.dir=${java.io.tmpdir}/outbox-test-staging"
})
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

    @Autowired
    private LocalFileStaging localFileStaging;

    @BeforeEach
    void setUp() {
        postFileRepository.deleteAll();
        fileStoragePort.clear();
    }

    @Test
    void should_upload_to_s3_and_save_post_file() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream("test content".getBytes(StandardCharsets.UTF_8)),
                "test.txt"
        );
        String finalPath = "post/1/test.txt";

        OutboxEventContext eventContext = createEventContext(1L, localPath, finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    void should_succeed_when_s3_already_has_file_and_local_file_is_missing() {
        String finalPath = "post/1/test.txt";
        fileStoragePort.addObject(finalPath);

        OutboxEventContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    void should_throw_when_local_file_missing_and_s3_does_not_have_file() {
        String finalPath = "post/1/test.txt";

        OutboxEventContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        assertThrows(IllegalStateException.class, () -> handler.handle(eventContext));
        assertEquals(0, postFileRepository.count());
    }

    @Test
    void should_succeed_without_duplicate_when_same_post_file_already_exists() {
        String finalPath = "post/1/test.txt";
        postFileRepository.save(PostFile.create(1L, finalPath, 100L, "text/plain"));

        OutboxEventContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    void should_throw_when_existing_post_file_metadata_conflicts() {
        String finalPath = "post/1/test.txt";
        postFileRepository.save(PostFile.create(2L, finalPath, 100L, "text/plain"));

        OutboxEventContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        assertThrows(IllegalStateException.class, () -> handler.handle(eventContext));
        assertEquals(1, postFileRepository.count());
    }

    private OutboxEventContext createEventContext(Long postId, String localPath, String finalPath) {
        PostFileUploadRequestedEvent payload = new PostFileUploadRequestedEvent(
                postId,
                localPath,
                finalPath,
                100L,
                "text/plain"
        );
        String serializedPayload = outboxPayloadSerializer.serialize(payload);
        return new OutboxEventContext(1L, OutboxEventNames.POST_FILE_UPLOAD, serializedPayload, "owner-1");
    }
}
