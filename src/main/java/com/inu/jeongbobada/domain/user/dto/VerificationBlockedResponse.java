package com.inu.jeongbobada.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

// 승인 대기·반려 로그인 거절 응답의 data. 승인 대기는 신청일, 반려는 반려 사유만 내려준다 (와이어프레임 2번 팝업).
// 반려 사유는 관리자가 비워 둘 수 있어서 반려여도 없을 수 있다
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationBlockedResponse(
    @Nullable LocalDateTime requestedAt,
    @Nullable String rejectionReason
) {
    public static VerificationBlockedResponse pending(LocalDateTime requestedAt) {
        return new VerificationBlockedResponse(requestedAt, null);
    }

    public static VerificationBlockedResponse rejected(@Nullable String rejectionReason) {
        return new VerificationBlockedResponse(null, rejectionReason);
    }
}
