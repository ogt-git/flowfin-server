package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AssetItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetItemRepository extends JpaRepository<AssetItem, Integer> {

    Optional<AssetItem> findByAccountIdAndItemCode(Integer accountId, String itemCode);

    List<AssetItem> findAllByAccountId(Integer accountId);
}
