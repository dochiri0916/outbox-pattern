package com.dochiri.outboxpattern.infrastructure.adapter.out.persistence;

import com.dochiri.outboxpattern.application.post.port.out.PostFileRepository;
import com.dochiri.outboxpattern.domain.PostFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostFileJpaAdapter implements PostFileRepository {

    private final PostFileJpaRepository repository;

    @Override
    public PostFile save(PostFile postFile) {
        return repository.save(postFile);
    }

    @Override
    public PostFile loadById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 파일을 찾을 수 없습니다."));
    }

}
