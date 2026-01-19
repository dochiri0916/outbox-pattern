package com.example.outboxpattern.domain.blog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.util.Objects.requireNonNull;

@Entity
@Table(
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

    public static PostFile create(Long postId, String storageKey, long fileSize, String contentType) {
        PostFile postFile = new PostFile();
        postFile.postId = requireNonNull(postId);
        postFile.storageKey = requireNonNull(storageKey);
        postFile.fileSize = fileSize;
        postFile.contentType = requireNonNull(contentType);
        return postFile;
    }

}