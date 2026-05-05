package com.dochiri.outboxpattern.infrastructure.adapter.out.persistence;

import com.dochiri.outboxpattern.domain.PostFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostFileJpaRepository extends JpaRepository<PostFile, Long> {

}
