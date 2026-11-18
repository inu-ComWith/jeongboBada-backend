package com.inu.jeongbobada.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.multipart.MultipartFile;

// 반려 후 서류 다시 제출 (multipart/form-data). 반려 사용자는 토큰이 없어서 학번 + 비밀번호로 본인을 확인한다 (#142).
// 학번·비밀번호 검증 문구는 로그인(LoginRequest)과 같다
public record VerificationResubmitRequest(
    @NotBlank(message = "학번을 입력해주세요")
    @Pattern(regexp = "\\d{9}", message = "학번은 숫자 9자리로 입력해주세요")
    String studentId,

    @NotBlank(message = "비밀번호를 입력해주세요")
    String password,

    // 비어 있을 때 기본 400 대신 FILE_EMPTY로 안내하도록 여기서는 검사하지 않고 FileStorage가 검사한다 (가입과 같음)
    @Schema(description = "새 재학 증빙 서류 (pdf·jpg, 최대 10MB, 1개)", type = "string", format = "binary")
    MultipartFile file
) {
}
