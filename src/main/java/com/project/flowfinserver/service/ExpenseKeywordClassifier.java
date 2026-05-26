package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.MatchType;
import com.project.flowfinserver.domain.MerchantCategoryRule;
import com.project.flowfinserver.repository.MerchantCategoryRuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-based 가맹점 분류기.
 * 앱 시작 시 DB 규칙 전체를 메모리에 로드하여 매 분류 요청마다 DB I/O 없이 O(1) EXACT 조회,
 * O(n) CONTAINS 조회를 수행한다. (n = CONTAINS 규칙 수, 약 200건 이하 예상)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseKeywordClassifier {

    private final MerchantCategoryRuleRepository ruleRepository;

    private Map<String, MerchantCategoryRule> exactRules = new HashMap<>();  // keyword → rule (O(1))
    private List<MerchantCategoryRule> containsRules = new ArrayList<>(); // priority 내림차순

    @PostConstruct
    public void init() {
        reloadRules();
    }

    public void reloadRules() {
        List<MerchantCategoryRule> all = ruleRepository.findAllByOrderByPriorityDesc();

        Map<String, MerchantCategoryRule> newExact = new HashMap<>();
        List<MerchantCategoryRule> newContains = new ArrayList<>();

        for (MerchantCategoryRule rule : all) {
            if (rule.getMatchType() == MatchType.EXACT) {
                newExact.put(rule.getKeyword(), rule);
            } else {
                newContains.add(rule);
            }
        }

        exactRules = newExact;
        containsRules = newContains;
        log.info("[KeywordClassifier] 규칙 로드 완료 — EXACT={}건 CONTAINS={}건",
                exactRules.size(), containsRules.size());
    }

    /**
     * 가맹점명을 분류하여 매칭된 규칙을 반환한다.
     * EXACT 매칭 우선, 없으면 CONTAINS, 모두 없으면 null 반환.
     */
    public MerchantCategoryRule classify(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) return null;

        // 1단계: EXACT (완전 일치)
        MerchantCategoryRule exactMatch = exactRules.get(merchantName);
        if (exactMatch != null) return exactMatch;

        // 2단계: CONTAINS (priority 내림차순으로 이미 정렬됨)
        for (MerchantCategoryRule rule : containsRules) {
            if (merchantName.contains(rule.getKeyword())) {
                return rule;
            }
        }

        return null;
    }
}
