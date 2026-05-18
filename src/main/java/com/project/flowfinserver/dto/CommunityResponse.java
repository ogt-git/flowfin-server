package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.Community;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
public class CommunityResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final Long userId;
    private final String category;
    private final int views;
    private final int likeCount;
    private final String createdAt;
    private final List<CommentResponse> comments;

    public CommunityResponse(Community community) {
        this.id = community.getId();
        this.title = community.getTitle();
        this.content = community.getContent();
        this.userId = community.getUserId();
        this.category = community.getCategory();
        this.views = community.getViews();
        this.likeCount = community.getLikeCount();
        this.createdAt = community.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        this.comments = List.of();
    }
}
