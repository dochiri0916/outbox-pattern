package com.dochiri.outboxpattern.application.blog;

import com.dochiri.outboxpattern.application.storage.port.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePostFacade {

    private final FileStoragePort fileStoragePort;
    private final CreatePostUseCase createPostUseCase;

    public CreatePostUseCase.Output execute(CreatePostUseCase.Input input, MultipartFile file) {
        String temporaryStorageKey = generateTemporaryStorageKey(file.getOriginalFilename());

        CreatePostUseCase.Input useCaseInput = new CreatePostUseCase.Input(
                        input.title(),
                        input.content(),
                        temporaryStorageKey,
                        file.getOriginalFilename(),
                        file.getSize(),
                        file.getContentType()
        );

        return createPostUseCase.execute(useCaseInput);
    }

    private void uploadToTemporaryStorage(MultipartFile file, String temporaryStorageKey) {
        try {
            fileStoragePort.upload(
                    temporaryStorageKey,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload file to temporary storage", e);
        }
    }

    private String generateTemporaryStorageKey(String originalFileName) {
        return "temporary/%s/%s".formatted(UUID.randomUUID(), originalFileName);
    }

}