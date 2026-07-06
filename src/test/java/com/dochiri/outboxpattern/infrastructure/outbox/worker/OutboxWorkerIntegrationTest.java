package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.infrastructure.adapter.out.persistence.PostFileJpaRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.storage.local.LocalFileStaging;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "app.staging.dir=${java.io.tmpdir}/outbox-test-staging"
})
@Import(TestStorageConfiguration.class)
class OutboxWorkerIntegrationTest {

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private PostFileJpaRepository postFileRepository;

    @Autowired
    private OutboxPayloadSerializer outboxPayloadSerializer;

    @Autowired
    private InMemoryFileStoragePort fileStoragePort;

    @Autowired
    private LocalFileStaging localFileStaging;

    @BeforeEach
    void setUp() {
        postFileRepository.deleteAll();
        outboxEventRepository.deleteAll();
        fileStoragePort.clear();
    }

    @Test
    void should_complete_event_when_handler_succeeds() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream("test content".getBytes(StandardCharsets.UTF_8)),
                "source.txt"
        );
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, localPath, finalPath);

        outboxWorker.runOnce();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.COMPLETED, updated.getStatus());
        assertEquals(0, updated.getRetryCount());
        assertEquals(1, postFileRepository.count());
    }

    @Test
    void should_retry_and_return_pending_when_handler_fails() {
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, "/tmp/nonexistent/file.txt", finalPath);

        outboxWorker.runOnce();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
        assertNotNull(updated.getNextRetryAt());
        assertNotNull(updated.getLastErrorMessage());
        assertEquals(0, postFileRepository.count());
    }

    @Test
    void should_mark_failed_after_max_retry_count() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream("test content".getBytes(StandardCharsets.UTF_8)),
                "source.txt"
        );
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, localPath, finalPath);

        OutboxEventContext processingEvent = outboxStatusService.markProcessing(event.getId());
        outboxStatusService.markFailed(
                event.getId(),
                processingEvent.processingOwnerId(),
                1,
                "failed"
        );

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
        assertNotNull(updated.getFailedAt());
    }

    @Test
    void should_recover_timed_out_processing_event_to_pending() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream("test content".getBytes(StandardCharsets.UTF_8)),
                "source.txt"
        );
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, localPath, finalPath);
        event.processing(LocalDateTime.now().minusMinutes(10), "owner-1");
        outboxEventRepository.save(event);

        outboxWorker.recoverTimedOutProcessingEvents();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
        assertEquals("PROCESSING timed out", updated.getLastErrorMessage());
        assertNotNull(updated.getNextRetryAt());
    }

    private OutboxEvent createPendingEvent(Long postId, String localPath, String finalPath) {
        PostFileUploadRequestedEvent payload = new PostFileUploadRequestedEvent(
                postId,
                localPath,
                finalPath,
                100L,
                "text/plain"
        );
        String serialized = outboxPayloadSerializer.serialize(payload);
        OutboxEvent event = OutboxEvent.create(
                OutboxEventNames.POST,
                postId,
                OutboxEventNames.POST_FILE_UPLOAD,
                serialized
        );
        return outboxEventRepository.save(event);
    }
}
