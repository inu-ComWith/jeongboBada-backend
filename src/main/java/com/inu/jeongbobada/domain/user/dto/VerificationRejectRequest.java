package com.inu.jeongbobada.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 거절 사유는 필수다 (와이어프레임 B-2 "반려 사유 (필수)"). 반려된 사용자가 로그인 팝업과 메일에서
// 무엇을 고쳐 다시 낼지 알 수 있어야 하기 때문이다. 입력값은 거절 메일에 그대로 쓰인다
public record VerificationRejectRequest(
    @NotBlank(message = "거절 사유는 필수입니다.")
    @Size(max = 200, message = "거절 사유는 200자 이하여야 합니다.")
    String reason
) {
}
