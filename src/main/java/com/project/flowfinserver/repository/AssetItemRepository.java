package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AssetItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetItemRepository extends JpaRepository<AssetItem, Long> {

    void deleteByAccountId(Long accountId);
}
