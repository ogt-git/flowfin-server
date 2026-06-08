package com.project.flowfinserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikeResponse {
    private final boolean liked;
    private final int likeCount;
}
