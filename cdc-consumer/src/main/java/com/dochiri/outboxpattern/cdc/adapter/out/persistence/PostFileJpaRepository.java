package com.dochiri.outboxpattern.cdc.adapter.out.persistence;

import com.dochiri.outboxpattern.domain.PostFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostFileJpaRepository extends JpaRepository<PostFile, Long> {

    boolean existsByStorageKey(String storageKey);

    Optional<PostFile> findByStorageKey(String storageKey);

}
