package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AssetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetItemRepository extends JpaRepository<AssetItem, Integer> {

    void deleteByAccountId(Long accountId);

    void deleteAllByUserId(Long userId);

    Optional<AssetItem> findByAccountIdAndItemCode(Integer accountId, String itemCode);

    List<AssetItem> findAllByAccountId(Integer accountId);

    @Modifying
    @Query("DELETE FROM AssetItem a WHERE a.accountId = :accountId AND a.itemCode NOT IN :itemCodes")
    void deleteByAccountIdAndItemCodeNotIn(@Param("accountId") Integer accountId,
                                           @Param("itemCodes") Collection<String> itemCodes);
}
