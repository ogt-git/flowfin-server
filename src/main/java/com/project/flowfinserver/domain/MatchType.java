package com.project.flowfinserver.domain;

// 가맹점 카테고리 규칙의 매칭 방식
public enum MatchType {
    EXACT,    // 가맹점명 완전 일치
    CONTAINS  // 가맹점명 포함 (substring)
}
