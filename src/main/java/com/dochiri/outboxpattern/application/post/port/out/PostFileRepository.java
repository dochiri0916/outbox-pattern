package com.dochiri.outboxpattern.application.post.port.out;

import com.dochiri.outboxpattern.domain.PostFile;

public interface PostFileRepository {

    PostFile save(PostFile postFile);

    PostFile loadById(Long id);

}
