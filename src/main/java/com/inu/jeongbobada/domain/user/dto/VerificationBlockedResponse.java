package com.inu.jeongbobada.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

// 승인 대기·반려 로그인 거절 응답의 data. 승인 대기는 신청일, 반려는 반려 사유와 닉네임을 내려준다 (와이어프레임 로그인 7·8번 팝업).
// 닉네임은 반려 후 "서류 다시 올리기" 화면(11번)이 잠긴 칸으로 보여준다. 반려 사용자는 토큰이 없어 내 정보 조회로 가져올 수 없다.
// 비밀번호가 맞을 때만 이 응답이 나가므로 닉네임이 남에게 드러나지 않는다.
// 반려 사유는 필수가 되기(#189) 전에 반려된 계정이면 없을 수 있다
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationBlockedResponse(
    @Nullable LocalDateTime requestedAt,
    @Nullable String rejectionReason,
    @Nullable String nickname
) {
    public static VerificationBlockedResponse pending(LocalDateTime requestedAt) {
        return new VerificationBlockedResponse(requestedAt, null, null);
    }

    public static VerificationBlockedResponse rejected(@Nullable String rejectionReason, String nickname) {
        return new VerificationBlockedResponse(null, rejectionReason, nickname);
    }
}
