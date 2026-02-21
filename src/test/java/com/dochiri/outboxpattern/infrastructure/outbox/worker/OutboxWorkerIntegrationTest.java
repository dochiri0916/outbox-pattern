package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.common.outbox.PostFileUploadPayload;
import com.dochiri.outboxpattern.domain.blog.PostFileRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.AggregateType;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventType;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import(TestStorageConfiguration.class)
class OutboxWorkerIntegrationTest {

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PostFileRepository postFileRepository;

    @Autowired
    private OutboxPayloadSerializer outboxPayloadSerializer;

    @Autowired
    private InMemoryFileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        postFileRepository.deleteAll();
        outboxEventRepository.deleteAll();
        fileStoragePort.clear();
    }

    @Test
    void should_complete_event_when_handler_succeeds() {
        String temporaryPath = "temporary/test/source.txt";
        String finalPath = "post/1/source.txt";
        fileStoragePort.addObject(temporaryPath);

        OutboxEvent event = createPendingEvent(1L, temporaryPath, finalPath);

        outboxWorker.runOnce();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.COMPLETED, updated.getStatus());
        assertEquals(0, updated.getRetryCount());
        assertEquals(1, postFileRepository.count());
    }

    @Test
    void should_retry_and_return_pending_when_handler_fails() {
        String temporaryPath = "temporary/test/source.txt";
        String finalPath = "post/1/source.txt";
        fileStoragePort.addObject(temporaryPath);
        fileStoragePort.setForcedExists(false);

        OutboxEvent event = createPendingEvent(1L, temporaryPath, finalPath);

        outboxWorker.runOnce();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
        assertEquals(0, postFileRepository.count());
    }

    @Test
    void should_mark_failed_after_max_retry_count() {
        String temporaryPath = "temporary/test/source.txt";
        String finalPath = "post/1/source.txt";
        fileStoragePort.addObject(temporaryPath);
        fileStoragePort.setForcedExists(false);

        OutboxEvent event = createPendingEvent(1L, temporaryPath, finalPath);

        for (int i = 0; i < 5; i++) {
            outboxWorker.runOnce();
        }

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, updated.getStatus());
        assertEquals(5, updated.getRetryCount());
    }

    private OutboxEvent createPendingEvent(Long postId, String temporaryPath, String finalPath) {
        PostFileUploadPayload payload = new PostFileUploadPayload(
                postId,
                temporaryPath,
                finalPath,
                100L,
                "text/plain"
        );
        String serialized = outboxPayloadSerializer.serialize(payload);
        OutboxEvent event = OutboxEvent.create(
                AggregateType.POST,
                postId,
                OutboxEventType.POST_FILE_UPLOAD,
                serialized
        );
        return outboxEventRepository.save(event);
    }
}
