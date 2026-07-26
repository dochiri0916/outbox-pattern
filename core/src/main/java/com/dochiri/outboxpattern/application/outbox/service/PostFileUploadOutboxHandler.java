package com.dochiri.outboxpattern.application.outbox.service;

import com.dochiri.outboxpattern.application.post.event.PostFileUploadRequestedEvent;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxEventHandler;
import com.dochiri.outboxpattern.application.outbox.port.in.OutboxProcessingContext;
import com.dochiri.outboxpattern.application.post.port.in.CompletePostFileUploadUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CompletePostFileUploadCommand;
import com.dochiri.outboxpattern.application.post.port.out.PostFileRepository;
import com.dochiri.outboxpattern.application.storage.port.out.MultipartFileStoragePort;
import com.dochiri.outboxpattern.application.outbox.model.OutboxEffect;
import com.dochiri.outboxpattern.application.outbox.model.OutboxEffectPart;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxFailureCode;
import com.dochiri.outboxpattern.application.outbox.OutboxEventNames;
import com.dochiri.outboxpattern.application.outbox.failure.OutboxProcessingException;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxEffectRepository;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxEffectPartRepository;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxPayloadSerializer;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxProgressPort;
import com.dochiri.outboxpattern.application.outbox.port.out.OutboxProcessingProperties;
import com.dochiri.outboxpattern.application.storage.port.out.FileStagingPort;
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
    private final FileStagingPort localFileStaging;
    private final OutboxEffectRepository outboxEventEffectRepository;
    private final OutboxEffectPartRepository outboxEventEffectPartRepository;
    private final OutboxProgressPort outboxStatusService;
    private final OutboxProcessingProperties outboxWorkerProperties;

    @Override
    public boolean supports(String eventType) {
        return OutboxEventNames.POST_FILE_UPLOAD.equals(eventType);
    }

    @Override
    public void handle(OutboxProcessingContext eventContext) {
        PostFileUploadRequestedEvent event = deserialize(eventContext.payload());
        OutboxEffect effect = findOrCreateEffect(eventContext);

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

    private OutboxEffect findOrCreateEffect(OutboxProcessingContext eventContext) {
        return outboxEventEffectRepository.findByOutboxEventId(eventContext.id())
                .orElseGet(() -> outboxEventEffectRepository.create(
                        OutboxEffect.inProgress(eventContext.id(), eventContext.eventType())
                ));
    }

    private void uploadMultipart(
            PostFileUploadRequestedEvent event,
            OutboxProcessingContext eventContext,
            OutboxEffect effect
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
        Map<Integer, OutboxEffectPart> completedParts = completedParts(eventContext.id());
        long processedBytes = completedParts.values().stream()
                .mapToLong(OutboxEffectPart::contentLength)
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
                OutboxEffectPart completedPart = completedParts.get(partNumber);

                if (completedPart == null) {
                    MultipartFileStoragePort.MultipartUploadPart uploadedPart = fileStoragePort.uploadPart(
                            upload,
                            partNumber,
                            new ByteArrayInputStream(content),
                            partSize
                    );
                    completedPart = outboxEventEffectPartRepository.create(new OutboxEffectPart(
                            eventContext.id(),
                            partNumber,
                            uploadedPart.eTag(),
                            uploadedPart.contentLength()
                    ));
                    completedParts.put(partNumber, completedPart);
                    processedBytes += partSize;
                    effect.progress(processedBytes, LocalDateTime.now());
                    outboxEventEffectRepository.update(effect);
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
                            .sorted(Comparator.comparingInt(OutboxEffectPart::partNumber))
                            .map(part -> new MultipartFileStoragePort.MultipartUploadPart(
                                    part.partNumber(),
                                    part.eTag(),
                                    part.contentLength()
                            ))
                            .toList()
            );
        } catch (RuntimeException exception) {
            throw OutboxProcessingException.retryable(OutboxFailureCode.STORAGE_UNAVAILABLE, exception);
        }
        completeEffect(effect);
    }

    private MultipartFileStoragePort.MultipartUploadSession multipartUpload(
            OutboxEffect effect,
            PostFileUploadRequestedEvent event
    ) {
        if (effect.multipartUploadId() == null) {
            MultipartFileStoragePort.MultipartUploadSession upload = fileStoragePort.initiateMultipartUpload(
                    event.storageKey(),
                    event.contentType()
            );
            effect.startMultipartUpload(upload.uploadId());
            outboxEventEffectRepository.update(effect);
            return upload;
        }
        return new MultipartFileStoragePort.MultipartUploadSession(
                event.storageKey(),
                effect.multipartUploadId()
        );
    }

    private Map<Integer, OutboxEffectPart> completedParts(Long outboxEventId) {
        Map<Integer, OutboxEffectPart> parts = new HashMap<>();
        outboxEventEffectPartRepository.findByOutboxEventId(outboxEventId)
                .forEach(part -> parts.put(part.partNumber(), part));
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

    private void completeEffect(OutboxEffect effect) {
        if (!effect.isCompleted()) {
            effect.complete(LocalDateTime.now());
            outboxEventEffectRepository.update(effect);
        }
    }

}
