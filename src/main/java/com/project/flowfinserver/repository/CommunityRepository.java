package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    List<Community> findAllByCategoryOrderByCreatedAtDesc(String category);

    List<Community> findAllByOrderByCreatedAtDesc();

    List<Community> findAllByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            String title, String content);

    void deleteAllByUserId(Long userId);
}