package com.dochiri.outboxpattern.adapter.out.persistence;

import com.dochiri.outboxpattern.application.post.port.out.PostRepository;
import com.dochiri.outboxpattern.domain.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostJpaAdapter implements PostRepository {

    private final PostJpaRepository repository;

    @Override
    public Post save(Post post) {
        return repository.save(post);
    }

    @Override
    public Post loadById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));
    }

}
