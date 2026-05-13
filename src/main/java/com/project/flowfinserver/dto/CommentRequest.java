package com.project.flowfinserver.dto;

import lombok.Getter;

@Getter
public class CommentRequest {
    private String content;
    private boolean anonymous;
}
