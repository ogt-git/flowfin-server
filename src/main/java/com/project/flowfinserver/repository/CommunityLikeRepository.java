package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.domain.CommunityLike;
import com.project.flowfinserver.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, Long> {
    Optional<CommunityLike> findByUserAndCommunity(User user, Community community);
}
