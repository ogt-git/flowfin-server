package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.CommunityLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, Long> {
    Optional<CommunityLike> findByUserIdAndCommunityId(Long userId, Long communityId);
}
