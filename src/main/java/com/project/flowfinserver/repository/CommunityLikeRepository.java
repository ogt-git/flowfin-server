package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.CommunityLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, Long> {
    Optional<CommunityLike> findByUserIdAndCommunityId(Long userId, Long communityId);

    @Modifying
    @Query("DELETE FROM CommunityLike cl WHERE cl.communityId = :communityId")
    void deleteAllByCommunityId(@Param("communityId") Long communityId);

    void deleteAllByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM CommunityLike cl WHERE cl.communityId IN (SELECT c.id FROM Community c WHERE c.userId = :userId)")
    void deleteAllByCommunityOwnerId(@Param("userId") Long userId);
}
