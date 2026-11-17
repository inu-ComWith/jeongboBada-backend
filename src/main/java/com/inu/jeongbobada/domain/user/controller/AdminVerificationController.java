package com.inu.jeongbobada.domain.user.controller;

import com.inu.jeongbobada.domain.user.dto.VerificationDetailResponse;
import com.inu.jeongbobada.domain.user.dto.VerificationRejectRequest;
import com.inu.jeongbobada.domain.user.dto.VerificationSummaryResponse;
import com.inu.jeongbobada.domain.user.entity.VerificationStatus;
import com.inu.jeongbobada.domain.user.service.AdminVerificationService;
import com.inu.jeongbobada.global.common.ApiResponse;
import com.inu.jeongbobada.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 관리자 가입 검토 (#142 2단계). /api/admin/** 는 SecurityConfig에서 ADMIN만 허용한다
// (토큰 없으면 401, 일반 사용자면 403).
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequestMapping("/api/admin/verifications")
@RequiredArgsConstructor
public class AdminVerificationController {

    private final AdminVerificationService adminVerificationService;

    @Operation(summary = "가입 검토 목록", description = "PENDING은 먼저 가입한 순, APPROVED·REJECTED는 최근 처리한 순")
    @GetMapping
    public ResponseEntity<ApiResponse<List<VerificationSummaryResponse>>> getList(@RequestParam VerificationStatus status) {
        ApiResponse<List<VerificationSummaryResponse>> response = ApiResponse.ok(adminVerificationService.getList(status));
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "가입 검토 상세", description = "documentUrl은 5분 뒤 만료된다. 만료되면 다시 조회해서 새 주소를 받는다")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<VerificationDetailResponse>> getDetail(@PathVariable Long userId) {
        ApiResponse<VerificationDetailResponse> response = ApiResponse.ok(adminVerificationService.getDetail(userId));
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "가입 승인", description = "서류를 삭제하고 승인 메일을 보낸다")
    @PostMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long userId) {
        adminVerificationService.approve(userId);

        ApiResponse<Void> response = ApiResponse.ok(null);
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "가입 거절", description = "거절 사유는 선택. 서류를 삭제하고 반려 메일을 보낸다")
    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
        @PathVariable Long userId,
        @Valid @RequestBody VerificationRejectRequest request
    ) {
        adminVerificationService.reject(userId, request.reason());

        ApiResponse<Void> response = ApiResponse.ok(null);
        return ResponseEntity.status(response.httpStatus()).body(response);
    }
}
