package com.dochiri.outboxpattern.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

@Entity
@Table(
        name = "post_files",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_post_file_storage_key",
                        columnNames = {"storageKey"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private String contentType;

    public PostFile(Long postId, String storageKey, long fileSize, String contentType) {
        this.postId = requireNonNull(postId);
        this.storageKey = requireNonNull(storageKey);
        this.fileSize = fileSize;
        this.contentType = requireNonNull(contentType);
    }

    public static PostFile create(Long postId, String storageKey, long fileSize, String contentType) {
        return new PostFile(postId, storageKey, fileSize, contentType);
    }

}
