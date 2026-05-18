package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "community")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private String category;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int views;

    @Column(name = "like_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int likeCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Community create(Long userId, String title, String content, String category) {
        Community community = new Community();
        community.userId = userId;
        community.title = title;
        community.content = content;
        community.category = category;
        community.views = 0;
        community.likeCount = 0;
        return community;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void increaseViews() {
        this.views++;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        this.likeCount--;
    }
}
