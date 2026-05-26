package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.user.TendencyRequest;
import com.project.flowfinserver.dto.user.UpdateProfileRequest;
import com.project.flowfinserver.dto.user.UserProfileResponse;
import com.project.flowfinserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "마이페이지 조회", description = "로그인한 사용자의 프로필, 연동 계좌 목록을 반환합니다.")
    @GetMapping("/myprofile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(userId)));
    }

    @Operation(summary = "프로필 수정", description = "이름과 투자 성향을 수정합니다.")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "프로필이 업데이트되었습니다."));
    }

    @Operation(summary = "투자 성향 변경", description = "마이페이지에서 투자 성향을 변경합니다.")
    @PostMapping("/tendency")
    public ResponseEntity<ApiResponse<Void>> updateTendency(
            @RequestBody @Valid TendencyRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.updateTendency(userId, request.getRiskType());
        return ResponseEntity.ok(ApiResponse.success(null, "투자 성향이 변경되었습니다."));
    }

    @Operation(summary = "회원 탈퇴", description = "본인 계정을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getPrincipal();
        userService.deleteUser(id, requesterId);
        return ResponseEntity.ok(ApiResponse.success(null, "회원 탈퇴가 완료되었습니다."));
    }
}
