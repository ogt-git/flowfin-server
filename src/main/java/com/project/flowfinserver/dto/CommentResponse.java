package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.Comment;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class CommentResponse {

    private final Long id;
    private final Long userId;
    private final String author;
    private final String content;
    private final boolean isAnonymous;
    private final String createdAt;

    public CommentResponse(Comment comment, String authorName) {
        this.id = comment.getId();
        this.userId = comment.isAnonymous() ? null : comment.getUserId();
        this.author = comment.isAnonymous() ? "익명" : authorName;
        this.content = comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent();
        this.isAnonymous = comment.isAnonymous();
        this.createdAt = comment.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }
}
