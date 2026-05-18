package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.MatchType;
import com.project.flowfinserver.domain.MerchantCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MerchantCategoryRuleRepository extends JpaRepository<MerchantCategoryRule, Long> {

    List<MerchantCategoryRule> findAllByOrderByPriorityDesc();

    Optional<MerchantCategoryRule> findFirstByKeywordAndMatchTypeOrderByPriorityDesc(
            String keyword, MatchType matchType);

    // JPQL은 LIMIT 미지원 → native query 사용
    @Query(value = "SELECT * FROM merchant_category_rule " +
                   "WHERE match_type = 'CONTAINS' AND :merchantName LIKE CONCAT('%', keyword, '%') " +
                   "ORDER BY priority DESC LIMIT 1",
           nativeQuery = true)
    Optional<MerchantCategoryRule> findFirstContainingKeyword(@Param("merchantName") String merchantName);
}
