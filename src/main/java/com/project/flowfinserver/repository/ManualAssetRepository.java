package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualAssetRepository extends JpaRepository<ManualAsset, Long> {

    List<ManualAsset> findAllByUserId(Long userId);

    List<ManualAsset> findByUserIdAndAssetTypeIn(Long userId, List<ManualAssetType> types);

    Optional<ManualAsset> findByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);
}
