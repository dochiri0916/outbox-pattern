package com.dochiri.outboxpattern.infrastructure.outbox.worker;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.infrastructure.adapter.out.persistence.PostFileJpaRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventStatus;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureType;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureCode;
import com.dochiri.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventEffectRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventEffectPartRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.storage.local.LocalFileStaging;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import io.micrometer.core.instrument.MeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "outbox.worker.processing-timeout=200ms",
        "app.staging.dir=${java.io.tmpdir}/outbox-test-staging"
})
@Import({TestStorageConfiguration.class, OutboxWorkerIntegrationTest.BlockingHandlerConfiguration.class})
class OutboxWorkerIntegrationTest {

    private static final String BLOCKING_EVENT_TYPE = "TEST_BLOCKING_EVENT";
    private static final String HEARTBEAT_EVENT_TYPE = "TEST_HEARTBEAT_EVENT";
    private static volatile CountDownLatch handlerStarted = new CountDownLatch(1);
    private static volatile CountDownLatch releaseHandler = new CountDownLatch(1);
    private static volatile CountDownLatch heartbeatObserved = new CountDownLatch(1);
    private static volatile LocalDateTime initialLeaseUntil;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventEffectRepository outboxEventEffectRepository;

    @Autowired
    private OutboxEventEffectPartRepository outboxEventEffectPartRepository;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private PostFileJpaRepository postFileRepository;

    @Autowired
    private OutboxPayloadSerializer outboxPayloadSerializer;

    @Autowired
    private InMemoryFileStoragePort fileStoragePort;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private LocalFileStaging localFileStaging;

    @BeforeEach
    void setUp() {
        postFileRepository.deleteAll();
        outboxEventEffectPartRepository.deleteAll();
        outboxEventEffectRepository.deleteAll();
        outboxEventRepository.deleteAll();
        fileStoragePort.clear();
        handlerStarted = new CountDownLatch(1);
        releaseHandler = new CountDownLatch(1);
        heartbeatObserved = new CountDownLatch(1);
        initialLeaseUntil = null;
    }

    @Test
    @DisplayName("handler가 성공하면 outbox 이벤트를 완료 처리한다")
    void should_complete_event_when_handler_succeeds() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "source.txt"
        );
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, localPath, finalPath);

        outboxWorker.runOnce();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.COMPLETED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertEquals(0, updated.getRetryCount());
        assertEquals(1, postFileRepository.count());
    }

    @Test
    @DisplayName("staging 파일이 없으면 영구 실패로 분류한다")
    void should_mark_event_failed_when_staged_file_is_missing() {
        // given
        String finalPath = "post/1/source.txt";
        OutboxEvent event = createPendingEvent(1L, "/tmp/nonexistent/file.txt", finalPath);

        // when
        outboxWorker.runOnce();

        // then
        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertEquals(0, updated.getRetryCount());
        assertEquals(OutboxFailureType.PERMANENT, updated.getFailureType());
        assertEquals(OutboxFailureCode.INVALID_STAGED_FILE, updated.getFailureCode());
        assertNotNull(updated.getFirstFailedAt());
        assertNull(updated.getNextRetryAt());
        assertNotNull(updated.getLastErrorMessage());
        assertEquals(0, postFileRepository.count());
    }

    @Test
    @DisplayName("일시적인 storage 오류는 재시도 대기 상태로 둔다")
    void should_return_pending_when_storage_failure_is_retryable() {
        // given
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "source.txt"
        );
        OutboxEvent event = createPendingEvent(1L, localPath, "post/1/retryable.txt");
        fileStoragePort.failNextMultipartPart(1);

        // when
        outboxWorker.runOnce();

        // then
        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, updated.getStatus());
        assertEquals(OutboxFailureType.RETRYABLE, updated.getFailureType());
        assertEquals(OutboxFailureCode.STORAGE_UNAVAILABLE, updated.getFailureCode());
        assertNotNull(updated.getNextRetryAt());
    }

    @Test
    @DisplayName("지원하지 않는 event type은 즉시 FAILED 처리한다")
    void should_mark_unsupported_event_as_failed_without_retry() {
        // given
        OutboxEvent event = createEvent("UNSUPPORTED_EVENT", "{}");

        // when
        outboxWorker.runOnce();

        // then
        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, updated.getStatus());
        assertEquals(OutboxFailureType.PERMANENT, updated.getFailureType());
        assertEquals(OutboxFailureCode.UNSUPPORTED_EVENT_TYPE, updated.getFailureCode());
        assertNull(updated.getNextRetryAt());
    }

    @Test
    @DisplayName("잘못된 payload는 즉시 FAILED 처리한다")
    void should_mark_invalid_payload_as_failed_without_retry() {
        // given
        OutboxEvent event = createEvent(OutboxEventNames.POST_FILE_UPLOAD, "not-json");

        // when
        outboxWorker.runOnce();

        // then
        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, updated.getStatus());
        assertEquals(OutboxFailureType.PERMANENT, updated.getFailureType());
        assertEquals(OutboxFailureCode.INVALID_PAYLOAD, updated.getFailureCode());
        assertNull(updated.getNextRetryAt());
    }

    @Test
    @DisplayName("이벤트 처리 결과를 성공 메트릭으로 기록한다")
    void should_record_processing_success_metric() {
        // given
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "metrics.txt"
        );
        createPendingEvent(1L, localPath, "post/1/metrics.txt");

        // when
        outboxWorker.runOnce();

        // then
        assertEquals(1.0, meterRegistry.get("outbox.processing.success")
                .tag("event_type", OutboxEventNames.POST_FILE_UPLOAD)
                .counter()
                .count());
    }

    @Test
    @DisplayName("허용된 재시도가 없으면 이벤트를 FAILED로 전이한다")
    void should_mark_failed_after_max_retry_count() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "source.txt"
        );
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, localPath, finalPath);

        OutboxEventContext processingEvent = outboxStatusService.markProcessing(event.getId());
        outboxStatusService.markFailed(
                event.getId(),
                processingEvent.processingOwnerId(),
                0,
                "failed"
        );

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertEquals(0, updated.getRetryCount());
        assertNotNull(updated.getFailedAt());
    }

    @Test
    @DisplayName("lease가 만료된 processing 이벤트를 재시도 대기 상태로 복구한다")
    void should_recover_timed_out_processing_event_to_pending() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "source.txt"
        );
        String finalPath = "post/1/source.txt";

        OutboxEvent event = createPendingEvent(1L, localPath, finalPath);
        LocalDateTime attemptStartedAt = LocalDateTime.now().minusMinutes(10);
        event.processing(attemptStartedAt, "owner-1", attemptStartedAt.plusMinutes(4));
        outboxEventRepository.save(event);

        outboxWorker.recoverTimedOutProcessingEvents();

        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertEquals(0, updated.getRetryCount());
        assertEquals("PROCESSING timed out", updated.getLastErrorMessage());
        assertNotNull(updated.getNextRetryAt());
    }

    @Test
    @DisplayName("배치 뒤쪽 이벤트는 실제 handler 시작 직전까지 PROCESSING이 되지 않는다")
    void should_claim_each_event_only_when_its_handler_is_about_to_start() throws Exception {
        // given
        OutboxEvent firstEvent = createEvent(BLOCKING_EVENT_TYPE, "first");
        OutboxEvent secondEvent = createEvent(BLOCKING_EVENT_TYPE, "second");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> workerTask = executor.submit(outboxWorker::runOnce);

        try {
            // when
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
            OutboxEvent queuedEvent = outboxEventRepository.findById(secondEvent.getId()).orElseThrow();

            // then
            assertEquals(OutboxEventStatus.PENDING, queuedEvent.getStatus());
            assertNull(queuedEvent.getAttemptStartedAt());
        } finally {
            releaseHandler.countDown();
            workerTask.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        assertEquals(OutboxEventStatus.COMPLETED, outboxEventRepository.findById(firstEvent.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("처리 중 heartbeat가 lease를 연장해 장시간 작업의 recovery를 막는다")
    void should_extend_lease_while_handler_is_running() throws Exception {
        // given
        OutboxEvent event = createEvent(HEARTBEAT_EVENT_TYPE, "heartbeat");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> workerTask = executor.submit(outboxWorker::runOnce);

        try {
            // when
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));
            boolean heartbeatSent = heartbeatObserved.await(5, TimeUnit.SECONDS);

            // then
            assertTrue(heartbeatSent);
            OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
            assertTrue(updated.getLeaseUntil().isAfter(initialLeaseUntil));
        } finally {
            releaseHandler.countDown();
            workerTask.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        assertEquals(OutboxEventStatus.COMPLETED, outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("progress가 멈추면 heartbeat가 lease를 연장하지 않고 recovery된다")
    void should_recover_event_when_progress_stops() throws Exception {
        // given
        OutboxEvent event = createEvent("TEST_STALLED_EVENT", "stalled");
        OutboxEventContext processingEvent = outboxStatusService.markProcessing(event.getId());
        new CountDownLatch(1).await(350, TimeUnit.MILLISECONDS);

        // when
        boolean heartbeatSent = outboxStatusService.heartbeat(
                event.getId(),
                processingEvent.processingOwnerId()
        );
        outboxWorker.recoverTimedOutProcessingEvents();

        // then
        assertFalse(heartbeatSent);
        OutboxEvent recovered = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, recovered.getStatus());
        assertNull(recovered.getLeaseUntil());
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

    private OutboxEvent createEvent(String eventType, String payload) {
        return outboxEventRepository.save(OutboxEvent.create(
                OutboxEventNames.POST,
                1L,
                eventType,
                payload
        ));
    }

    private byte[] sampleFile() {
        return "0123456789".repeat(10).getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BlockingHandlerConfiguration {

        @Bean
        OutboxEventHandler blockingHandler(OutboxEventRepository outboxEventRepository) {
            return new OutboxEventHandler() {
                @Override
                public boolean supports(String eventType) {
                    return BLOCKING_EVENT_TYPE.equals(eventType) || HEARTBEAT_EVENT_TYPE.equals(eventType);
                }

                @Override
                public void handle(OutboxEventContext eventContext) {
                    handlerStarted.countDown();
                    if (HEARTBEAT_EVENT_TYPE.equals(eventContext.eventType())) {
                        observeHeartbeat(eventContext, outboxEventRepository);
                    }
                    try {
                        if (!releaseHandler.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Test handler release timed out");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Test handler interrupted", e);
                    }
                }

                private void observeHeartbeat(
                        OutboxEventContext eventContext,
                        OutboxEventRepository outboxEventRepository
                ) {
                    initialLeaseUntil = outboxEventRepository.findById(eventContext.id())
                            .orElseThrow()
                            .getLeaseUntil();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                    while (System.nanoTime() < deadline) {
                        LocalDateTime leaseUntil = outboxEventRepository.findById(eventContext.id())
                                .orElseThrow()
                                .getLeaseUntil();
                        if (leaseUntil.isAfter(initialLeaseUntil)) {
                            heartbeatObserved.countDown();
                            return;
                        }
                        Thread.yield();
                    }
                }
            };
        }
    }
}
