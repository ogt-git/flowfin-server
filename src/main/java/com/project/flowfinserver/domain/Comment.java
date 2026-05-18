package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_anonymous", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isAnonymous;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDeleted;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Comment create(Long communityId, Long userId, String content, boolean isAnonymous) {
        Comment comment = new Comment();
        comment.communityId = communityId;
        comment.userId = userId;
        comment.content = content;
        comment.isAnonymous = isAnonymous;
        comment.isDeleted = false;
        return comment;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
