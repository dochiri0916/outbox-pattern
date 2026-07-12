package com.dochiri.outboxpattern.infrastructure.outbox.handler.post;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.post.port.in.CompletePostFileUploadUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CompletePostFileUploadCommand;
import com.dochiri.outboxpattern.application.post.port.out.PostFileRepository;
import com.dochiri.outboxpattern.application.storage.port.out.MultipartFileStoragePort;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventEffect;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxEventEffectPart;
import com.dochiri.outboxpattern.infrastructure.outbox.entity.OutboxFailureCode;
import com.dochiri.outboxpattern.infrastructure.outbox.recorder.OutboxEventNames;
import com.dochiri.outboxpattern.infrastructure.outbox.failure.OutboxProcessingException;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventEffectRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.repository.OutboxEventEffectPartRepository;
import com.dochiri.outboxpattern.infrastructure.outbox.serializer.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.infrastructure.outbox.handler.OutboxEventHandler;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxEventContext;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxStatusService;
import com.dochiri.outboxpattern.infrastructure.outbox.worker.OutboxWorkerProperties;
import com.dochiri.outboxpattern.infrastructure.storage.local.LocalFileStaging;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostFileUploadOutboxHandler implements OutboxEventHandler {

    private final CompletePostFileUploadUseCase completePostFileUploadUseCase;
    private final OutboxPayloadSerializer outboxPayloadSerializer;
    private final MultipartFileStoragePort fileStoragePort;
    private final PostFileRepository postFileRepository;
    private final LocalFileStaging localFileStaging;
    private final OutboxEventEffectRepository outboxEventEffectRepository;
    private final OutboxEventEffectPartRepository outboxEventEffectPartRepository;
    private final OutboxStatusService outboxStatusService;
    private final OutboxWorkerProperties outboxWorkerProperties;

    @Override
    public boolean supports(String eventType) {
        return OutboxEventNames.POST_FILE_UPLOAD.equals(eventType);
    }

    @Override
    public void handle(OutboxEventContext eventContext) {
        PostFileUploadRequestedEvent event = deserialize(eventContext.payload());
        OutboxEventEffect effect = findOrCreateEffect(eventContext);

        if (alreadyCompleted(event)) {
            localFileStaging.delete(event.localFilePath());
            completeEffect(effect);
            return;
        }

        if (!effect.isCompleted()) {
            if (!storageObjectExists(event.storageKey())) {
                uploadMultipart(event, eventContext, effect);
            } else {
                completeEffect(effect);
            }
        }
        localFileStaging.delete(event.localFilePath());

        try {
            completePostFileUploadUseCase.complete(new CompletePostFileUploadCommand(
                    event.postId(),
                    event.storageKey(),
                    event.fileSize(),
                    event.contentType()
            ));
        } catch (IllegalStateException exception) {
            throw OutboxProcessingException.permanent(
                    OutboxFailureCode.AGGREGATE_STATE_CONFLICT,
                    exception
            );
        }
    }

    private PostFileUploadRequestedEvent deserialize(String payload) {
        try {
            return outboxPayloadSerializer.deserialize(payload, PostFileUploadRequestedEvent.class);
        } catch (RuntimeException exception) {
            throw OutboxProcessingException.permanent(OutboxFailureCode.INVALID_PAYLOAD, exception);
        }
    }

    private boolean storageObjectExists(String storageKey) {
        try {
            return fileStoragePort.exists(storageKey);
        } catch (RuntimeException exception) {
            throw OutboxProcessingException.retryable(OutboxFailureCode.STORAGE_UNAVAILABLE, exception);
        }
    }

    private boolean alreadyCompleted(PostFileUploadRequestedEvent event) {
        return postFileRepository.findByStorageKey(event.storageKey())
                .map(postFile -> {
                    if (postFile.hasSameMetadata(event.postId(), event.fileSize(), event.contentType())) {
                        return true;
                    }
                    throw OutboxProcessingException.permanent(
                            OutboxFailureCode.AGGREGATE_STATE_CONFLICT,
                            new IllegalStateException(
                                    "PostFile metadata conflicts with storageKey: " + event.storageKey()
                            )
                    );
                })
                .orElse(false);
    }

    private OutboxEventEffect findOrCreateEffect(OutboxEventContext eventContext) {
        return outboxEventEffectRepository.findByOutboxEventId(eventContext.id())
                .orElseGet(() -> outboxEventEffectRepository.save(
                        OutboxEventEffect.inProgress(eventContext.id(), eventContext.eventType())
                ));
    }

    private void uploadMultipart(
            PostFileUploadRequestedEvent event,
            OutboxEventContext eventContext,
            OutboxEventEffect effect
    ) {
        if (event.fileSize() == 0) {
            try (InputStream inputStream = readStagedFile(event.localFilePath())) {
                fileStoragePort.uploadIfAbsent(event.storageKey(), inputStream, 0, event.contentType());
            } catch (OutboxProcessingException exception) {
                throw exception;
            } catch (Exception exception) {
                throw OutboxProcessingException.retryable(
                        OutboxFailureCode.STORAGE_UNAVAILABLE,
                        exception
                );
            }
            completeEffect(effect);
            return;
        }

        MultipartFileStoragePort.MultipartUploadSession upload = multipartUpload(effect, event);
        Map<Integer, OutboxEventEffectPart> completedParts = completedParts(eventContext.id());
        long processedBytes = completedParts.values().stream()
                .mapToLong(OutboxEventEffectPart::getContentLength)
                .sum();

        try (InputStream inputStream = readStagedFile(event.localFilePath())) {
            long remainingBytes = event.fileSize();
            int partNumber = 1;
            while (remainingBytes > 0) {
                int partSize = (int) Math.min(
                        outboxWorkerProperties.multipartPartSizeBytes(),
                        remainingBytes
                );
                byte[] content = readPart(inputStream, partSize, event.localFilePath());
                OutboxEventEffectPart completedPart = completedParts.get(partNumber);

                if (completedPart == null) {
                    MultipartFileStoragePort.MultipartUploadPart uploadedPart = fileStoragePort.uploadPart(
                            upload,
                            partNumber,
                            new ByteArrayInputStream(content),
                            partSize
                    );
                    completedPart = outboxEventEffectPartRepository.save(OutboxEventEffectPart.completed(
                            eventContext.id(),
                            partNumber,
                            uploadedPart.eTag(),
                            uploadedPart.contentLength()
                    ));
                    completedParts.put(partNumber, completedPart);
                    processedBytes += partSize;
                    effect.progress(processedBytes, LocalDateTime.now());
                    outboxEventEffectRepository.save(effect);
                    outboxStatusService.recordProgress(
                            eventContext.id(),
                            eventContext.processingOwnerId()
                    );
                }

                remainingBytes -= partSize;
                partNumber++;
            }
            if (inputStream.read() != -1) {
                throw new IllegalStateException("Staged file size does not match event payload: " + event.localFilePath());
            }
        } catch (OutboxProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw OutboxProcessingException.retryable(
                    OutboxFailureCode.STORAGE_UNAVAILABLE,
                    exception
            );
        }

        outboxStatusService.recordProgress(
                eventContext.id(),
                eventContext.processingOwnerId()
        );
        try {
            fileStoragePort.completeMultipartUpload(
                    upload,
                    completedParts.values().stream()
                            .sorted(Comparator.comparingInt(OutboxEventEffectPart::getPartNumber))
                            .map(part -> new MultipartFileStoragePort.MultipartUploadPart(
                                    part.getPartNumber(),
                                    part.getETag(),
                                    part.getContentLength()
                            ))
                            .toList()
            );
        } catch (RuntimeException exception) {
            throw OutboxProcessingException.retryable(OutboxFailureCode.STORAGE_UNAVAILABLE, exception);
        }
        completeEffect(effect);
    }

    private MultipartFileStoragePort.MultipartUploadSession multipartUpload(
            OutboxEventEffect effect,
            PostFileUploadRequestedEvent event
    ) {
        if (effect.getMultipartUploadId() == null) {
            MultipartFileStoragePort.MultipartUploadSession upload = fileStoragePort.initiateMultipartUpload(
                    event.storageKey(),
                    event.contentType()
            );
            effect.startMultipartUpload(upload.uploadId());
            outboxEventEffectRepository.save(effect);
            return upload;
        }
        return new MultipartFileStoragePort.MultipartUploadSession(
                event.storageKey(),
                effect.getMultipartUploadId()
        );
    }

    private Map<Integer, OutboxEventEffectPart> completedParts(Long outboxEventId) {
        Map<Integer, OutboxEventEffectPart> parts = new HashMap<>();
        outboxEventEffectPartRepository.findByOutboxEventIdOrderByPartNumberAsc(outboxEventId)
                .forEach(part -> parts.put(part.getPartNumber(), part));
        return parts;
    }

    private byte[] readPart(InputStream inputStream, int partSize, String localFilePath) {
        try {
            byte[] content = inputStream.readNBytes(partSize);
            if (content.length != partSize) {
                throw OutboxProcessingException.permanent(
                        OutboxFailureCode.INVALID_STAGED_FILE,
                        new IllegalStateException("Staged file size does not match event payload: " + localFilePath)
                );
            }
            return content;
        } catch (OutboxProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw OutboxProcessingException.permanent(
                    OutboxFailureCode.INVALID_STAGED_FILE,
                    new IllegalStateException("Failed to read staged file: " + localFilePath, exception)
            );
        }
    }

    private InputStream readStagedFile(String localFilePath) {
        try {
            return localFileStaging.read(localFilePath);
        } catch (UncheckedIOException | IllegalArgumentException exception) {
            throw OutboxProcessingException.permanent(OutboxFailureCode.INVALID_STAGED_FILE, exception);
        }
    }

    private void completeEffect(OutboxEventEffect effect) {
        if (!effect.isCompleted()) {
            effect.complete(LocalDateTime.now());
            outboxEventEffectRepository.save(effect);
        }
    }

}
