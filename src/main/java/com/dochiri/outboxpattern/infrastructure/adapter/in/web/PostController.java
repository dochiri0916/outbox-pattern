package com.dochiri.outboxpattern.infrastructure.adapter.in.web;

import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.UploadPostFileUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.application.post.port.in.dto.UploadPostFileCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.UploadedPostFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final UploadPostFileUseCase uploadPostFileUseCase;
    private final CreatePostUseCase createPostUseCase;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CreatePostResult> create(@RequestPart CreatePostRequest request, @RequestPart MultipartFile file) {
        UploadedPostFile uploadedFile = uploadPostFile(file);
        CreatePostCommand command = new CreatePostCommand(
                request.title(),
                request.content(),
                uploadedFile.temporaryFilePath(),
                uploadedFile.originalFileName(),
                uploadedFile.fileSize(),
                uploadedFile.contentType()
        );

        return ResponseEntity.ok(createPostUseCase.execute(command));
    }

    private UploadedPostFile uploadPostFile(MultipartFile file) {
        try {
            return uploadPostFileUseCase.upload(new UploadPostFileCommand(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            ));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    public record CreatePostRequest(String title, String content) {
    }

}
