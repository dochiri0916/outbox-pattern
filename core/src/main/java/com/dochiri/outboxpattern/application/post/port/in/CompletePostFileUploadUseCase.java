package com.dochiri.outboxpattern.application.post.port.in;

import com.dochiri.outboxpattern.application.post.port.in.dto.CompletePostFileUploadCommand;

public interface CompletePostFileUploadUseCase {

    void complete(CompletePostFileUploadCommand command);

}
