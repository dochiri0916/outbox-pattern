package com.dochiri.outboxpattern.application.post.service;

import com.dochiri.outboxpattern.application.post.port.in.CreatePostUseCase;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;
import com.dochiri.outboxpattern.application.storage.port.out.FileStagingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreatePostService implements CreatePostUseCase {

    private final FileStagingPort fileStagingPort;
    private final CreatePostTransactional transactional;

    @Override
    public CreatePostResult execute(CreatePostCommand command) {
        String localFilePath = fileStagingPort.stage(
                command.inputStream(),
                command.originalFileName()
        );
        return transactional.create(command, localFilePath);
    }

}
