package com.dochiri.outboxpattern.infrastructure.adapter.in.web;

import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final CreatePostUseCase createPostUseCase;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CreatePostResult> create(
            @RequestPart CreatePostRequest request,
            @RequestPart MultipartFile file
    ) throws IOException {
        CreatePostCommand command = new CreatePostCommand(
                request.title(),
                request.content(),
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType()
        );
        return ResponseEntity.ok(createPostUseCase.execute(command));
    }

    public record CreatePostRequest(String title, String content) {
    }

}
