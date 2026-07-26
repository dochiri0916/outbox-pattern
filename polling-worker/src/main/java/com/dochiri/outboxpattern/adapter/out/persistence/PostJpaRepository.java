package com.dochiri.outboxpattern.adapter.out.persistence;

import com.dochiri.outboxpattern.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostJpaRepository extends JpaRepository<Post, Long> {

}