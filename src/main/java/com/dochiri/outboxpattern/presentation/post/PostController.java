package com.dochiri.outboxpattern.presentation.post;

import com.dochiri.outboxpattern.application.blog.CreatePostFacade;
import com.dochiri.outboxpattern.application.blog.CreatePostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final CreatePostFacade createPostFacade;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CreatePostUseCase.Output> create(@RequestPart CreatePostUseCase.Input input, @RequestPart MultipartFile file) {
        return ResponseEntity.ok(createPostFacade.execute(input, file));
    }

}