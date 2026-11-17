package com.inu.jeongbobada.domain.user.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

// 거절 사유는 선택 입력이다 (#142). 입력하면 거절 메일에 그대로 쓰인다
public record VerificationRejectRequest(
    @Size(max = 200, message = "거절 사유는 200자 이하여야 합니다.")
    @Nullable String reason
) {
}
