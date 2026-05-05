package com.dochiri.outboxpattern.application.post.port.out;

import com.dochiri.outboxpattern.domain.PostFile;

import java.util.Optional;

public interface PostFileRepository {

    PostFile save(PostFile postFile);

    PostFile loadById(Long id);

    boolean existsByStorageKey(String storageKey);

    Optional<PostFile> findByStorageKey(String storageKey);

}
