package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.dto.asset.ManualAssetRequest;
import com.project.flowfinserver.dto.asset.ManualAssetResponse;
import com.project.flowfinserver.exception.ErrorCode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.flowfinserver.repository.ManualAssetRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualAssetService {

    private final ManualAssetRepository manualAssetRepository;
    private final AssetService assetService;

    @Transactional
    public ManualAssetResponse save(Long userId, ManualAssetRequest request) {
        ManualAsset asset = ManualAsset.create(
                userId, request.assetType(), request.amount(), request.memo());
        ManualAsset saved = manualAssetRepository.save(asset);

        log.debug("[ManualAsset] 저장 userId={} type={} amount={}", userId, request.assetType(), request.amount());
        assetService.updateInvestableAmount(userId);
        return ManualAssetResponse.from(saved);
    }

    @Transactional
    public ManualAssetResponse update(Long userId, Long assetId, ManualAssetRequest request) {
        ManualAsset asset = findOwnedAsset(userId, assetId);
        asset.update(request.assetType(), request.amount(), request.memo());

        log.debug("[ManualAsset] 수정 userId={} assetId={}", userId, assetId);
        assetService.updateInvestableAmount(userId);
        return ManualAssetResponse.from(asset);
    }

    @Transactional
    public void delete(Long userId, Long assetId) {
        ManualAsset asset = findOwnedAsset(userId, assetId);
        manualAssetRepository.delete(asset);

        log.debug("[ManualAsset] 삭제 userId={} assetId={}", userId, assetId);
        assetService.updateInvestableAmount(userId);
    }

    @Transactional(readOnly = true)
    public List<ManualAssetResponse> getAll(Long userId) {
        return manualAssetRepository.findAllByUserId(userId)
                .stream()
                .map(ManualAssetResponse::from)
                .toList();
    }

    private ManualAsset findOwnedAsset(Long userId, Long assetId) {
        ManualAsset asset = manualAssetRepository.findById(assetId)
                .orElseThrow(() -> new EntityNotFoundException(
                        ErrorCode.MANUAL_ASSET_NOT_FOUND.getMessage()));
        if (!asset.getUserId().equals(userId)) {
            throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.getMessage());
        }
        return asset;
    }
}
