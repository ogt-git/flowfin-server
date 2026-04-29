package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.Comment;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class CommentResponse {

    private final Long id;
    private final String author;
    private final String content;
    private final boolean isAnonymous;
    private final String createdAt;

    public CommentResponse(Comment comment) {
        this.id = comment.getId();
        this.author = comment.isAnonymous() ? "익명" : comment.getAuthor().getName();
        this.content = comment.getContent();
        this.isAnonymous = comment.isAnonymous();
        this.createdAt = comment.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }
}