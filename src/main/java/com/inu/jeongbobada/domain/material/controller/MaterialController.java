package com.inu.jeongbobada.domain.material.controller;

import com.inu.jeongbobada.domain.material.dto.request.MaterialCreateRequest;
import com.inu.jeongbobada.domain.material.dto.response.MaterialCreateResponse;
import com.inu.jeongbobada.domain.material.dto.response.MaterialDetailResponse;
import com.inu.jeongbobada.domain.material.dto.response.MaterialDownloadResponse;
import com.inu.jeongbobada.domain.material.dto.response.MaterialPageResponse;
import com.inu.jeongbobada.domain.material.service.MaterialService;
import com.inu.jeongbobada.domain.user.security.CustomUserDetails;
import com.inu.jeongbobada.global.common.ApiResponse;
import com.inu.jeongbobada.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 강의별 학습자료 (#187). 학습자료는 로그인한 사용자만 볼 수 있다 (와이어프레임 "로그인 필수: 강의 상세, 후기, 학습자료").
// SecurityConfig에서 /api/courses/*/materials, /api/materials/** 를 authenticated()로 막는다
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME_NAME)
@RestController
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @Operation(summary = "학습자료 업로드", description = "제목 5~50자, 설명 10~500자, 유형 1개 이상, 파일 1개(pdf·jpg·png·pptx·docx·xlsx·hwp, 최대 20MB)")
    @PostMapping(value = "/api/courses/{courseId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MaterialCreateResponse>> create(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable Long courseId,
        @Valid @ModelAttribute MaterialCreateRequest request
    ) {
        Long materialId = materialService.create(user.getUserId(), courseId, request);

        ApiResponse<MaterialCreateResponse> response = ApiResponse.created(new MaterialCreateResponse(materialId));
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "학습자료 목록", description = "내 글이 맨 위, 그다음 최신순. page는 0부터, size는 1~50 (무한 스크롤은 hasNext가 false가 될 때까지 page를 올린다)")
    @GetMapping("/api/courses/{courseId}/materials")
    public ResponseEntity<ApiResponse<MaterialPageResponse>> getPage(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable Long courseId,
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 50, message = "size는 50 이하여야 합니다.") int size
    ) {
        ApiResponse<MaterialPageResponse> response = ApiResponse.ok(materialService.getPage(user.getUserId(), courseId, page, size));
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "학습자료 상세")
    @GetMapping("/api/materials/{materialId}")
    public ResponseEntity<ApiResponse<MaterialDetailResponse>> getDetail(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable Long materialId
    ) {
        ApiResponse<MaterialDetailResponse> response = ApiResponse.ok(materialService.getDetail(user.getUserId(), materialId));
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "학습자료 내려받기 주소", description = "5분 뒤 만료되는 주소. 열면 원래 파일명으로 저장된다")
    @GetMapping("/api/materials/{materialId}/download")
    public ResponseEntity<ApiResponse<MaterialDownloadResponse>> getDownloadUrl(@PathVariable Long materialId) {
        ApiResponse<MaterialDownloadResponse> response = ApiResponse.ok(materialService.getDownloadUrl(materialId));
        return ResponseEntity.status(response.httpStatus()).body(response);
    }

    @Operation(summary = "학습자료 삭제", description = "작성자만 삭제할 수 있다. 첨부 파일도 함께 지운다")
    @DeleteMapping("/api/materials/{materialId}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable Long materialId
    ) {
        materialService.delete(user.getUserId(), materialId);

        ApiResponse<Void> response = ApiResponse.ok(null);
        return ResponseEntity.status(response.httpStatus()).body(response);
    }
}
