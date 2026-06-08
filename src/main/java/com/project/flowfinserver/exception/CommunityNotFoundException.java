package com.project.flowfinserver.exception;

public class CommunityNotFoundException extends RuntimeException {
    public CommunityNotFoundException(Long id) {
        super("게시글을 찾을 수 없습니다. id=" + id);
    }
}
