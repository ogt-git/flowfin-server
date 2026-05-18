package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;
import com.project.flowfinserver.dto.asset.ManualAssetRequest;
import com.project.flowfinserver.dto.asset.ManualAssetResponse;
import com.project.flowfinserver.repository.ManualAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ManualAssetServiceTest {

    @Mock ManualAssetRepository manualAssetRepository;
    @Mock AssetService assetService;

    @InjectMocks
    ManualAssetService manualAssetService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    // ==================== save ====================

    @Test
    @DisplayName("save — 신규 수동자산 저장 후 updateInvestableAmount 호출")
    void save_저장후_investable_amount_갱신() {
        ManualAssetRequest req = new ManualAssetRequest(ManualAssetType.CASH, 500_000L, "지갑 현금");
        ManualAsset saved = ManualAsset.create(USER_ID, ManualAssetType.CASH, 500_000L, "지갑 현금");
        ReflectionTestUtils.setField(saved, "id", 1L);
        given(manualAssetRepository.save(any(ManualAsset.class))).willReturn(saved);

        ManualAssetResponse response = manualAssetService.save(USER_ID, req);

        ArgumentCaptor<ManualAsset> captor = ArgumentCaptor.forClass(ManualAsset.class);
        then(manualAssetRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getAssetType()).isEqualTo(ManualAssetType.CASH);
        assertThat(captor.getValue().getAmount()).isEqualTo(500_000L);
        assertThat(response.id()).isEqualTo(1L);
        then(assetService).should(times(1)).updateInvestableAmount(USER_ID);
    }

    // ==================== update ====================

    @Test
    @DisplayName("update — 본인 자산 수정 후 investable_amount 갱신")
    void update_본인자산_수정_후_investable_갱신() {
        ManualAsset existing = ManualAsset.create(USER_ID, ManualAssetType.CASH, 300_000L, "기존");
        ReflectionTestUtils.setField(existing, "id", 1L);
        given(manualAssetRepository.findById(1L)).willReturn(Optional.of(existing));

        ManualAssetRequest req = new ManualAssetRequest(ManualAssetType.DEPOSIT, 1_000_000L, "변경됨");
        manualAssetService.update(USER_ID, 1L, req);

        assertThat(existing.getAssetType()).isEqualTo(ManualAssetType.DEPOSIT);
        assertThat(existing.getAmount()).isEqualTo(1_000_000L);
        then(assetService).should(times(1)).updateInvestableAmount(USER_ID);
    }

    @Test
    @DisplayName("update — 타인 자산 접근 시 AccessDeniedException 발생")
    void update_타인자산_접근시_예외() {
        ManualAsset other = ManualAsset.create(OTHER_USER_ID, ManualAssetType.CASH, 100_000L, null);
        ReflectionTestUtils.setField(other, "id", 2L);
        given(manualAssetRepository.findById(2L)).willReturn(Optional.of(other));

        assertThatThrownBy(() -> manualAssetService.update(USER_ID, 2L,
                new ManualAssetRequest(ManualAssetType.CASH, 100_000L, null)))
                .isInstanceOf(AccessDeniedException.class);

        then(assetService).should(never()).updateInvestableAmount(any());
    }

    @Test
    @DisplayName("update — 존재하지 않는 자산 접근 시 EntityNotFoundException 발생")
    void update_미존재_자산_예외() {
        given(manualAssetRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> manualAssetService.update(USER_ID, 99L,
                new ManualAssetRequest(ManualAssetType.CASH, 100_000L, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete — 본인 자산 삭제 후 investable_amount 갱신")
    void delete_본인자산_삭제_후_investable_갱신() {
        ManualAsset existing = ManualAsset.create(USER_ID, ManualAssetType.SAVINGS, 2_000_000L, null);
        ReflectionTestUtils.setField(existing, "id", 3L);
        given(manualAssetRepository.findById(3L)).willReturn(Optional.of(existing));

        manualAssetService.delete(USER_ID, 3L);

        then(manualAssetRepository).should(times(1)).delete(existing);
        then(assetService).should(times(1)).updateInvestableAmount(USER_ID);
    }

    // ==================== getAll ====================

    @Test
    @DisplayName("getAll — 사용자 수동자산 목록 반환")
    void getAll_목록_반환() {
        ManualAsset a1 = ManualAsset.create(USER_ID, ManualAssetType.CASH, 100_000L, null);
        ManualAsset a2 = ManualAsset.create(USER_ID, ManualAssetType.DEPOSIT, 5_000_000L, "KB 예금");
        ReflectionTestUtils.setField(a1, "id", 1L);
        ReflectionTestUtils.setField(a2, "id", 2L);
        given(manualAssetRepository.findAllByUserId(USER_ID)).willReturn(List.of(a1, a2));

        List<ManualAssetResponse> result = manualAssetService.getAll(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ManualAssetResponse::assetType)
                .containsExactly(ManualAssetType.CASH, ManualAssetType.DEPOSIT);
    }
}
