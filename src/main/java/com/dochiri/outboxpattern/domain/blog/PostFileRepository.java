package com.dochiri.outboxpattern.domain.blog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostFileRepository extends JpaRepository<PostFile, Long> {

}