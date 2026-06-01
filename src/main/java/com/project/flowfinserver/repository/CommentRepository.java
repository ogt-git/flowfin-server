package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByCommunityIdOrderByCreatedAtAsc(Long communityId);

    int countByCommunityId(Long communityId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.communityId = :communityId")
    void deleteAllByCommunityId(@Param("communityId") Long communityId);

    void deleteAllByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.communityId IN (SELECT com.id FROM Community com WHERE com.userId = :userId)")
    void deleteAllByCommunityOwnerId(@Param("userId") Long userId);
}
