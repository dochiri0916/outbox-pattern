package com.dochiri.outboxpattern.adapter.out.outbox.handler.post;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.outbox.service.PostFileUploadOutboxHandler;
import com.dochiri.outboxpattern.domain.PostFile;
import com.dochiri.outboxpattern.adapter.out.persistence.PostFileJpaRepository;
import com.dochiri.outboxpattern.adapter.out.outbox.entity.OutboxEvent;
import com.dochiri.outboxpattern.application.outbox.OutboxEventNames;
import com.dochiri.outboxpattern.adapter.out.outbox.repository.OutboxEventEffectRepository;
import com.dochiri.outboxpattern.adapter.out.outbox.repository.OutboxEventEffectPartRepository;
import com.dochiri.outboxpattern.adapter.out.outbox.repository.OutboxEventRepository;
import com.dochiri.outboxpattern.adapter.out.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxProcessingContext;
import com.dochiri.outboxpattern.adapter.in.outbox.worker.OutboxStatusService;
import com.dochiri.outboxpattern.adapter.out.storage.local.LocalFileStaging;
import com.dochiri.outboxpattern.support.InMemoryFileStoragePort;
import com.dochiri.outboxpattern.support.TestStorageConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "outbox.worker.multipart-part-size-bytes=4",
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
    private OutboxEventEffectRepository outboxEventEffectRepository;

    @Autowired
    private OutboxEventEffectPartRepository outboxEventEffectPartRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @Autowired
    private InMemoryFileStoragePort fileStoragePort;

    @Autowired
    private LocalFileStaging localFileStaging;

    @BeforeEach
    void setUp() {
        postFileRepository.deleteAll();
        outboxEventEffectPartRepository.deleteAll();
        outboxEventEffectRepository.deleteAll();
        outboxEventRepository.deleteAll();
        fileStoragePort.clear();
    }

    @Test
    @DisplayName("파일을 storage에 업로드하고 게시글 파일을 저장한다")
    void should_upload_to_s3_and_save_post_file() {
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "test.txt"
        );
        String finalPath = "post/1/test.txt";

        OutboxProcessingContext eventContext = createEventContext(1L, localPath, finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    @DisplayName("storage에 파일이 있으면 로컬 파일 없이도 성공한다")
    void should_succeed_when_s3_already_has_file_and_local_file_is_missing() {
        String finalPath = "post/1/test.txt";
        fileStoragePort.addObject(finalPath);

        OutboxProcessingContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    @DisplayName("로컬 파일과 storage 파일이 모두 없으면 실패한다")
    void should_throw_when_local_file_missing_and_s3_does_not_have_file() {
        String finalPath = "post/1/test.txt";

        OutboxProcessingContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        assertThrows(IllegalStateException.class, () -> handler.handle(eventContext));
        assertEquals(0, postFileRepository.count());
    }

    @Test
    @DisplayName("동일한 게시글 파일이 이미 있으면 중복 저장하지 않는다")
    void should_succeed_without_duplicate_when_same_post_file_already_exists() {
        String finalPath = "post/1/test.txt";
        postFileRepository.save(PostFile.create(1L, finalPath, 100L, "text/plain"));

        OutboxProcessingContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        handler.handle(eventContext);

        assertEquals(1, postFileRepository.count());
    }

    @Test
    @DisplayName("같은 outbox event를 재실행해도 외부 파일 side effect를 한 번만 수행한다")
    void should_apply_external_side_effect_once_for_same_event() {
        // given
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "test.txt"
        );
        String finalPath = "post/1/test.txt";
        OutboxProcessingContext eventContext = createEventContext(1L, localPath, finalPath);

        // when
        handler.handle(eventContext);
        handler.handle(eventContext);

        // then
        assertEquals(1, fileStoragePort.uploadCount(finalPath));
        assertEquals(1, outboxEventEffectRepository.count());
        assertEquals(25, outboxEventEffectPartRepository.count());
        assertEquals(1, postFileRepository.count());
    }

    @Test
    @DisplayName("실패 후 재실행하면 완료된 multipart part부터 재개한다")
    void should_resume_multipart_upload_from_completed_parts() {
        // given
        String localPath = localFileStaging.stage(
                new ByteArrayInputStream(sampleFile()),
                "test.txt"
        );
        String finalPath = "post/1/resume.txt";
        OutboxProcessingContext eventContext = createEventContext(1L, localPath, finalPath);
        fileStoragePort.failNextMultipartPart(2);

        // when
        assertThrows(IllegalStateException.class, () -> handler.handle(eventContext));
        handler.handle(eventContext);

        // then
        assertEquals(25, outboxEventEffectPartRepository.count());
        assertEquals(1, fileStoragePort.multipartPartUploadCount(finalPath, 1));
        assertEquals(1, postFileRepository.count());
    }

    @Test
    @DisplayName("storage key의 게시글 파일 metadata가 다르면 실패한다")
    void should_throw_when_existing_post_file_metadata_conflicts() {
        String finalPath = "post/1/test.txt";
        postFileRepository.save(PostFile.create(2L, finalPath, 100L, "text/plain"));

        OutboxProcessingContext eventContext = createEventContext(1L, "/tmp/nonexistent/file.txt", finalPath);

        assertThrows(IllegalStateException.class, () -> handler.handle(eventContext));
        assertEquals(1, postFileRepository.count());
    }

    private OutboxProcessingContext createEventContext(Long postId, String localPath, String finalPath) {
        PostFileUploadRequestedEvent payload = new PostFileUploadRequestedEvent(
                postId,
                localPath,
                finalPath,
                100L,
                "text/plain"
        );
        String serializedPayload = outboxPayloadSerializer.serialize(payload);
        OutboxEvent outboxEvent = outboxEventRepository.save(OutboxEvent.create(
                OutboxEventNames.POST,
                postId,
                OutboxEventNames.POST_FILE_UPLOAD,
                serializedPayload
        ));
        return outboxStatusService.markProcessing(outboxEvent.getId());
    }

    private byte[] sampleFile() {
        return "0123456789".repeat(10).getBytes(StandardCharsets.UTF_8);
    }
}
