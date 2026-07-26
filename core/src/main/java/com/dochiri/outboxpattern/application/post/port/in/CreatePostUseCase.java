package com.dochiri.outboxpattern.application.post.port.in;

import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.CreatePostResult;

public interface CreatePostUseCase {

    CreatePostResult execute(CreatePostCommand command);

}
