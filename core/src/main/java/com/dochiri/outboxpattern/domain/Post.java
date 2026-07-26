package com.dochiri.outboxpattern.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.util.Objects.requireNonNull;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String content;

    private Post(String title, String content) {
        this.title = requireNonNull(title);
        this.content = content;
    }

    public static Post create(String title, String content) {
        return new Post(title, content);
    }

}
