package com.dochiri.outboxpattern.application.post.port.in;

import com.dochiri.outboxpattern.application.post.port.in.dto.UploadPostFileCommand;
import com.dochiri.outboxpattern.application.post.port.in.dto.UploadedPostFile;

public interface UploadPostFileUseCase {

    UploadedPostFile upload(UploadPostFileCommand command);

}
