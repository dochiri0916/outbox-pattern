package com.dochiri.outboxpattern.application.post.port.out;

import com.dochiri.outboxpattern.domain.Post;

public interface PostRepository {

    Post save(Post post);

    Post loadById(Long id);

}
